; Asynchonous HTTP server, based on http-kit and core.async

(ns full.http.server
  (:require [clojure.core.async :refer [go]]
            [ring.util.response :refer [content-type]]
            [compojure.response :as response]
            [compojure.core :as compojure]
            [compojure.handler :as handler]
            [org.httpkit.server :as httpkit]
            [full.core.sugar :refer :all]
            [full.core.log :as log]
            [full.async :refer :all]
            [full.json :refer [write-json]]
            [full.metrics :as metrics])
  (:import (clojure.core.async.impl.protocols ReadPort)
           (clojure.lang ExceptionInfo)
           (org.httpkit HttpStatus)))


;; MIDLEWARE


(defn json-response>
  "Middleware that converts responses with a map or a vector for a body into a
  JSON response."
  [handler>]
  (fn [request]
    (go-try
      (let [response (<? (handler> request))]
        (if (coll? (:body response))
          (let [json-response (if (contains? response :json-key-fn)
                                (update-in response [:body] #(write-json % :json-key-fn (:json-key-fn response)))
                                (update-in response [:body] write-json))]
            (if (contains? (:headers response) "Content-Type")
              json-response
              (content-type json-response "application/json; charset=utf-8")))
          response)))))

(defn normalize-response> [handler>]
  (fn [req]
    (go-try
      (let [res (<? (handler> req))]
        (assoc (or res {})
          :body (:body res)
          :status (or (:status res) 200)
          :headers (or (:headers res) {}))))))

(def default-logger (org.slf4j.LoggerFactory/getLogger "full.http.server.request"))

(defn response-error-status [ex]
  ; clojure.lang.ExceptionInfo with :status key
  (or (:status (ex-data ex))
      ; any other exception - internal server error
      500))

(defn response-error-message
  "Generate error message that will be returned in response."
  [ex]
  ; clojure.lang.ExceptionInfo with :user-message key
  (or (:user-message (ex-data ex))
      ; any other exception - just
      (.getReasonPhrase (HttpStatus/valueOf (response-error-status ex)))))

(defn log-error-message
  "Generate error message that will be written to log."
  [ex]
  (if (instance? ExceptionInfo ex)
    (.getMessage ex)
    ; Non-Clojure exception - use str instead of
    ; getMessages which will include exception class
    ; as Java exception messages alone are sometimes
    ; (such as for NPE) ironically useless
    (str ex)))

(defn basic-exception-renderer [ex]
  {:body (response-error-message ex)
   :headers {}
   :status (response-error-status ex)})

(defn json-exception-renderer [ex]
  (let [status (or (:status (ex-data ex)) 500)]
    {:body (write-json {:message (response-error-message ex)
                        :status status})
     :headers {}
     :status status}))

(defn server-error-exception-logger [ex]
  (when (>= (response-error-status ex) 500)
    ; only log exception stack traces for 50x errors
    (log/error ex (or (ex-data ex) (str ex)))))

(defn handle-exceptions>
  [handler> & {:keys [renderer logger]
               :or {renderer basic-exception-renderer
                    logger server-error-exception-logger}}]
  (fn [req]
    (go
      (try
        (<? (handler> req))
        (catch Exception ex
          (logger ex)
          (-> (renderer ex)
              (assoc :endpoint (-> ex (ex-data) :endpoint))
              (assoc :error (log-error-message ex))))))))

(def metric-states {:ok "ok"
                    :warn "warning"
                    :error "critical"})

(defn log-track-request>
  [handler> & {:keys [logger log-params]
               :or {logger default-logger
                    log-params {}}}]
  (fn [req]
    (go-try
      (let [start-time (time-bookmark)
            method (.toUpperCase (name (:request-method req)))
            uri (str (:uri req) (when-let [q (:query-string req)] (str "?" q)))
            res (<? (handler> req))
            status (:status res)
            req-time (ellapsed-time start-time)
            mdc (merge (or (:mdc res) {})
                       {:method method
                        :uri uri
                        :ua (get-in req [:headers "user-agent"])
                        :status status
                        :time (long req-time)
                        :endpoint (:endpoint res)}
                       (remap (:params req) log-params))
            severity (cond
                       (and (>= status 200) (< status 400)) :ok
                       (and (>= status 400) (< status 500)) :warn
                       :else :error)
            message (str status " "
                         method " "
                         uri " "
                         req-time "ms"
                         (if (= :ok severity) "" (str " " (get res :error))))]
        (log/with-mdc mdc
                      (case severity
                        :ok (.info logger message)
                        :warn (.warn logger message)
                        :error (.error logger message)))
        (metrics/track {:service (str "endpoint." method (:endpoint res))
                        :tags ["timeit"]
                        :metric req-time
                        :uri uri
                        :state (get metric-states severity)})
        res))))

(defn- send-async
  "Private middleware that sends the response asynchronously via http-kit's
  async support. Must be the last item in middleware stack."
  [handler>]
  (fn [request]
    (httpkit/with-channel
      request channel
      (go
        (try
          (if-let [response< (handler> request)]
            (let [response (<? response<)]
              (httpkit/send! channel response true))
            (httpkit/close channel))
          (catch Throwable e
            (log/error e "error sending async response")
            e))))))


;;; COMPOJURE HACKS
;;; neccessary to be able to pass response as channel instead of map


(extend-protocol response/Renderable
  ReadPort
  (render [ch _] ch))

(defn handle-head>
  "Middleware for handling HEAD requests in a way that doesn't break Compojure.
  This workarounds the logic in compojure.core/if-method that breaks when
  response is channel."
  [handler>]
  (fn [req]
    (if (= :head (:request-method req))
      (go-try
        (let [res (-> req (assoc :request-method :get) (handler>) (<?))]
          (assoc res :body nil)))
      (handler> req))))


;;; MIDLEWARE GLUE


(defn json-api
  [routes & {:keys [exception-logger exception-renderer logger log-params]
             :or {exception-logger server-error-exception-logger
                  exception-renderer json-exception-renderer
                  logger default-logger
                  log-params {}}}]
  (-> (handler/api routes)
      (handle-head>)
      (normalize-response>)
      (json-response>)
      (handle-exceptions> :logger exception-logger
                          :renderer exception-renderer)
      (log-track-request> :logger logger
                          :log-params log-params)))


;;; ROUTING
;;; Extension to compojure routing:
;;; 1. associates endpoint (route) to response - useful for logging and
;;;    monitoring
;;; 2. async - processes route's body in a go block


; just alias Compjure's defmacro for convenience
(def #^{:macro true} defroutes #'compojure/defroutes)

(defmacro go-try-assoc-endpoint [endpoint & body]
  `(go-try
     (let [endpoint# ~endpoint]
       (try
         (assoc (<? (go-try ~@body)) :endpoint endpoint#)
         (catch Exception ex#
           ; wrap and attach endpoint
           (throw (ex-info (log-error-message ex#)
                           (assoc (or (ex-data ex#) {}) :endpoint endpoint#)
                           ex#)))))))

(defmacro GET [path args & body]
  `(compojure/GET ~path ~args (go-try-assoc-endpoint ~path ~@body)))

(defmacro POST [path args & body]
  `(compojure/POST ~path ~args (go-try-assoc-endpoint ~path ~@body)))

(defmacro PUT [path args & body]
  `(compojure/PUT ~path ~args (go-try-assoc-endpoint ~path ~@body)))

(defmacro DELETE [path args & body]
  `(compojure/DELETE ~path ~args (go-try-assoc-endpoint ~path ~@body)))

(defmacro HEAD [path args & body]
  `(compojure/HEAD ~path ~args (go-try-assoc-endpoint ~path ~@body)))

(defmacro OPTIONS [path args & body]
  `(compojure/OPTIONS ~path ~args (go-try-assoc-endpoint ~path ~@body)))

(defmacro PATCH [path args & body]
  `(compojure/PATCH ~path ~args (go-try-assoc-endpoint ~path ~@body)))

(defmacro ANY [path args & body]
  `(compojure/ANY ~path ~args (go-try-assoc-endpoint ~path ~@body)))


;;; SERVER


(defn run-server [handler opts]
  (httpkit/run-server (-> handler
                          (send-async))
                      opts))
