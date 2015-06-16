(ns full.aws.s3
  "Super-simple async S3 client for storing and fetching string or EDN objects."
  (:require [clojure.edn :as edn]
            [full.aws.core :as aws]
            [full.async :refer :all]
            [full.http.client :as http]
            [full.time :refer :all]
            [full.edn :refer [read-edn]])
  (:import (com.amazonaws.services.s3 AmazonS3Client)
           (com.amazonaws.services.s3.model GeneratePresignedUrlRequest)
           (java.util Date)
           (com.amazonaws HttpMethod)
           (java.io InputStream)))

(def client (delay (doto (AmazonS3Client. @aws/credentials)
                     (.setRegion @aws/region))))

(defn- expiration-date []
  (let [d (Date.)
        msec (.getTime d)]
    (.setTime d (+ msec (* 24 60 60 1000)))  ; 24h
    d))

(defn- unrwrap-etag
  "Etag header is wrapped in double quotes, simply substring it."
  [etag]
  (when (string? etag)
    (.substring etag 1 (dec (.length etag)))))

(defn- presign-request
  [bucket-name key & {:keys [method content-type]}]
  (-> (GeneratePresignedUrlRequest. bucket-name key)
      (.withExpiration (expiration-date))
      (cond-> method (.withMethod method)
              content-type (.withContentType content-type))))

(defn put-object>
  "Create or updates S3 object. Returns a channel that will yield single
  etag on success or exception on error.
  Optional parameters:
    * :headers - HTTP headers such as Content-Type
    * :timeout - request timeout in seconds"
  [^String bucket-name, ^String key, ^String body
   & {:keys [headers timeout]}]
  {:pre [bucket-name key body]}
  (go-try
    (let [content-type (or (get headers "Content-Type")
                           "text/plain")
          url (.generatePresignedUrl
                @client
                (presign-request bucket-name
                                 key
                                 :method HttpMethod/PUT
                                 :content-type content-type))
          headers (-> (or headers {})
                      (assoc "Content-Length" (.length body))
                      (assoc "Content-Type" content-type))]
      (-> (http/req> {:url (str url)
                      :method :put
                      :body body
                      :headers headers
                      :timeout timeout
                      ; we want raw response to access headers
                      :response-parser nil})
          (<?)
          (get-in [:headers :etag])
          (unrwrap-etag)))))

(defn put-edn>
  [^String bucket-name, ^String key, ^String object
  & {:keys [headers timeout]}]
  (put-object> bucket-name key (pr-str object)
               :headers (-> (or headers {})
                            (assoc "Content-Type" "application/edn"))
               :timeout timeout))

(defn- string-response-parser [res]
  (cond
    (string? res) res
    (instance? InputStream res) (slurp res)
    :else res))

(defn get-object>
  [^String bucket-name, ^String key
   & {:keys [headers timeout response-parser]
      :or {response-parser string-response-parser}}]
  (go-try
    (let [url (.generatePresignedUrl @client (presign-request bucket-name key))]
      (-> (http/req> {:url (str url)
                      :method :get
                      :headers headers
                      :timeout timeout})
          (<?)
          (response-parser)))))

(defn get-edn>
  [^String bucket-name, ^String key
   & {:keys [headers timeout]}]
  (get-object> bucket-name key
               :headers headers
               :timeout timeout
               :response-parser (comp read-edn string-response-parser)))