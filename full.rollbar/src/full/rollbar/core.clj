(ns full.rollbar.core
  (:require [full.http.client :refer [req>]]
            [full.async :refer [go-try <?? <?]]
            [full.core.sugar :refer [when->]]
            [camelsnake.core :refer [->snake_case]]
            [full.core.config :refer [opt]]
            [full.core.log :as log])
  (:import (java.net InetAddress)))

(def rollbar-access-token (opt [:rollbar :access-token] :default nil))
(def rollbar-environment (opt [:rollbar :environment] :default nil))

(def enabled? (delay (and (some? @rollbar-access-token) (some? @rollbar-environment))))
(def host (delay (.getHostAddress (InetAddress/getLocalHost))))

(defn- rollbar-req>
  [body]
  (req> {:base-url "https://api.rollbar.com"
         :resource "api/1/item/"
         :method :post
         :body body
         :body-json-key-fn ->snake_case}))

(defn root-exception
  [ex]
  (loop [ex ex]
    (if-let [cause (.getCause ex)]
      (recur cause)
      ex)))

(defn error-msg
  [exc]
  (let [message (.getMessage exc)]
    (if (empty? message)
      (.toString (.getClass exc))
      message)))

(defn- frame
  [row]
  {:filename (-> row .getFileName)
   :lineno (-> row .getLineNumber)
   :method (str (-> row .getClassName) "/" (-> row .getMethodName))})

(defn- frames
  [ex]
  (->> ex (.getStackTrace) (map frame)))

(defn- exception
  [ex]
  {:class (-> ex .getClass .toString)
   :message (error-msg ex)})

(defn trace-payload
  [ex]
  (let [root-ex (root-exception ex)]
    {:frames (frames root-ex)
     :exception (exception root-ex)}))

(defn request-payload
  [req]
  {:method (clojure.string/upper-case (name (:request-method req)))
   :url (:uri req)
   :headers (dissoc (:headers req) "authorization")
   :user_ip (:remote-addr req)})

(defn rollbar-payload
  [host access-token environment]
  {:access_token access-token
   :data {:environment environment
          :host host
          :language "clojure"
          :body {}}})

(defn report>
  [ex & [request]]
  (go-try
    (if @enabled?
      (-> (rollbar-payload @host @rollbar-access-token @rollbar-environment)
          (assoc-in [:data :body :trace] (trace-payload ex))
          (when-> request (assoc-in [:data :body :request] (request-payload request)))
          (rollbar-req>)
          (<?)))))

