(ns full.http.client
  (:require [clojure.core.async :refer [chan >! close!]]
            [clojure.string :refer [upper-case]]
            [org.httpkit.client :as httpkit]
            [camelsnake.core :refer [->camelCase]]
            [full.core.sugar :refer :all]
            [full.core.config :refer [defoptconfig]]
            [full.core.log :as log]
            [full.async :refer [go-try]]
            [full.json :refer [read-json write-json]]))


(defoptconfig http-timeout :http-timeout)

(def default-http-timeout 30)  ; seconds

(def connection-error-status 599)


;;; LOGGING
;;; idea is to log each status to different logger so that some statuses
;;; can be filtered in log config


(defn logger [status]
  (-> (str "full.http.client." status)
      (org.slf4j.LoggerFactory/getLogger)))

(defmacro log-error [status & message]
  `(-> (logger ~status)
       (.error (print-str ~@message))))

(defmacro log-warn [status & message]
  `(-> (logger ~status)
       (.warn (print-str ~@message))))

(defmacro log-debug [status & message]
  `(-> (logger ~status)
       (.debug (print-str ~@message))))


;;; REQUEST / RESPONSE HANDLING


(defn json-body? [body]
  (and body (map? body)))

(defn- request-body
  [body & {:keys [json-key-fn] :or {json-key-fn ->camelCase}}]
  (if (json-body? body)
    (write-json body :json-key-fn json-key-fn)
    body))

(defn- parse-content
  [status headers body]
  (if (and (not= status 204)  ; has content
           (.startsWith (:content-type headers "") "application/json"))
    (read-json body)
    body))

(defn- process-error-response
  [full-url status body cause]
  (let [status (if cause connection-error-status status)
        body (if cause (str cause) body)
        message (str "Error requesting " full-url ": "
                     (if cause
                       (str "Connection error " (str cause))
                       (str "HTTP Error " status)))
        ex (ex-info message {:status status, :body body} cause)]
    (if (>= status 500)
      (log-error status message)
      (log-warn status message))
    ex))

(defn- process-response
  [method full-url result-channel {:keys [opts status headers body error]}]
  (go-try
    (->> (if (or error (> status 299))
           (process-error-response full-url status body error)
           (let [res (if (= method :head) headers (parse-content status headers body))]
             (log-debug status
                        "Response " full-url
                        "status:" status
                        (when body (str "body:" body))
                        "headers:" headers)
             res))
         (>! result-channel))
    (close! result-channel)))


;;; REQUEST


(defn req>
  "Performs asynchronous API request. Always returns result channel which will
  return either response or exception."
  [{:keys [base-url resource url method params body headers timeout form-params
           body-json-key-fn]
    :or {method :get
         body-json-key-fn ->camelCase}}]
  {:pre [(or url (and base-url resource))]}
  (let [req {:url (or url (str base-url "/" resource))
             :method method
             :body (request-body body :json-key-fn body-json-key-fn)
             :query-params params
             :headers headers
             :form-params form-params
             :timeout (* (or timeout @http-timeout default-http-timeout) 1000)}
        full-url (str (upper-case (name method))
                      " " (:url req)
                      (if (not-empty (:query-params req))
                        (str "?" (query-string (:query-params req))) ""))
        result-channel (chan 1)]
    (log/debug "Request" full-url
               (if-let [body (:body req)] (str "body:" body) "")
               (if-let [headers (:headers req)] (str "headers:" headers) ""))
    (httpkit/request req (partial process-response method full-url result-channel))
    result-channel))
