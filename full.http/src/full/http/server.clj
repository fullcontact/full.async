; Asynchonous HTTP server, based on http-kit and core.async

(ns full.http.server
  (:require [clojure.core.async :refer [go go-loop <!]]
            [ring.util.response :refer [content-type]]
            [compojure.response :as response]
            [compojure.core :as compojure]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [resource-request]]
            [ring.middleware.content-type :refer [content-type-response]]
            [ring.util.codec :as codec]
            [ring.middleware.cookies :refer [cookies-request cookies-response]]
            [org.httpkit.server :as httpkit]
            [full.core.sugar :refer :all]
            [full.core.log :as log]
            [full.async :refer :all]
            [full.json :refer [write-json]]
            [full.metrics :as metrics]
            [ring.middleware.cors :as rc]
            [clojure.core.async :as async])
  (:import (clojure.core.async.impl.protocols ReadPort)
           (clojure.lang ExceptionInfo)
           (org.httpkit HttpStatus)))


;; MIDLEWARE


(defn- encode-json
  [chunk response]
  (str (if-let [json-key-fn (:json-key-fn response)]
         (write-json chunk :json-key-fn json-key-fn)
         (write-json chunk))
       \newline))

(defn json-response>
  "Middleware that converts responses with a map or a vector for a body into a
  JSON response."
  [handler>]
  (fn [request]
    (go-try
      (let [response (<? (handler> request))
            body (:body response)]
        (cond-> response
                ; collection - complete response
                (coll? body)
                  (assoc :body (encode-json body response))
                ; channel - streaming response
                (instance? ReadPort body)
                  (assoc :body (async/map #(if (coll? %)
                                            (encode-json % response)
                                            %)
                                          [body]))
                ; add json content-type
                (not (contains? (:headers response) "Content-Type"))
                  (content-type (str "application/json; charset=utf-8")))))))

(defn normalize-response> [handler>]
  (fn [req]
    (go-try
      (let [res (<? (handler> req))
            resm (if (map? res) res {:body res})]
        (assoc resm
          :body (:body res)
          :status (or (:status res) 200)
          :headers (or (:headers res) {}))))))

(defn wrap-cors>
  [handler & access-control]
  (let [access-control (rc/normalize-config access-control)]
    (fn [request]
      (go-try
        (cond
          (and (rc/preflight? request) (rc/allow-request? request access-control))
            (rc/add-access-control request access-control {:status 200 :headers {}})
          (rc/allow-request? request access-control)
            (rc/add-access-control request access-control (<? (handler request)))
          :else (<? (handler request)))))))

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
          (try
            (logger ex)
            (catch Throwable e
              (log/error e "Error logging error")))
          (try
            (-> (renderer ex)
                (assoc :endpoint (-> ex (ex-data) :endpoint))
                (assoc :error (log-error-message ex)))
            (catch Throwable e
              (log/error e "Error rendering error")
              {:body "Server Error"
               :headers {}
               :status 500})))))))

(def metric-states {:ok "ok"
                    :warn "warning"
                    :error "critical"})

(defn log-track-request>
  [handler> & {:keys [logger]
               :or {logger default-logger}}]
  (fn [req]
    (go-try
      (let [start-time (time-bookmark)
            method (.toUpperCase (name (:request-method req)))
            uri (str (:uri req) (when-let [q (:query-string req)] (str "?" q)))
            res (<? (handler> req))
            status (or (:status res)
                       (do
                         (log/error "Nil status, request:" req
                                    "response:" res)
                         500))
            req-time (ellapsed-time start-time)
            mdc (merge (or (:mdc res) {})
                       {:method method
                        :uri uri
                        :ua (get-in req [:headers "user-agent"])
                        :status status
                        :time (long req-time)
                        :endpoint (:endpoint res)})
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

(defn wrap-resource>
  [handler> root-path]
  (fn [req]
    (go-try
      (or (resource-request req root-path)
          (<? (handler> req))))))

(defn wrap-content-type>
  "Middleware that adds a content-type header to the response if one is not
  set by the handler. Uses the ring.util.mime-type/ext-mime-type function to
  guess the content-type from the file extension in the URI. If no
  content-type can be found, it defaults to 'application/octet-stream'.

  Accepts the following options:

  :mime-types - a map of filename extensions to mime-types that will be
                used in addition to the ones defined in
                ring.util.mime-types/default-mime-types

  This is async version of wrap-content-type from ring.middleware.content-type:
  https://github.com/mmcgrana/ring/blob/master/ring-core/src/ring/middleware/content_type.clj
  Copyright (c) 2009-2010 Mark McGranaghan"
  {:arglists '([handler] [handler options])}
  [handler> & [opts]]
  (fn [req]
    (go-try
      (if-let [resp (<? (handler> req))]
        (content-type-response resp req opts)))))

(defn wrap-cookies>
  "Parses the cookies in the request map, then assocs the resulting map
       to the :cookies key on the request.

       Accepts the following options:

       :decoder - a function to decode the cookie value. Expects a function that
                  takes a string and returns a string. Defaults to URL-decoding.

       :encoder - a function to encode the cookie name and value. Expects a
                  function that takes a name/value map and returns a string.
                  Defaults to URL-encoding.

       Each cookie is represented as a map, with its value being held in the
       :value key. A cookie may optionally contain a :path, :domain or :port
       attribute.

       To set cookies, add a map to the :cookies key on the response. The values
       of the cookie map can either be strings, or maps containing the following
       keys:

       :value     - the new value of the cookie
       :path      - the subpath the cookie is valid for
       :domain    - the domain the cookie is valid for
       :max-age   - the maximum age in seconds of the cookie
       :expires   - a date string at which the cookie will expire
       :secure    - set to true if the cookie requires HTTPS, prevent HTTP access
       :http-only - set to true if the cookie is valid for HTTP and HTTPS only
                    (ie. prevent JavaScript access)"
  {:arglists '([handler] [handler options])}
  [handler> & [{:keys [decoder encoder]
                :or {decoder codec/form-decode-str
                     encoder codec/form-encode}}]]
  (fn [req]
    (go-try
      (-> req
          (cookies-request {:decoder decoder})
          (handler>)
          <?
          (cookies-response {:encoder encoder})))))

(defn send-streaming-response
  [response channel]
  (go-loop []
    (let [chunk (<! (:body response))]
      (cond
        (instance? Throwable chunk)
          (httpkit/send! channel (json-exception-renderer chunk) true)
        (nil? chunk)
          (httpkit/close channel)
        :else
          (do
            (httpkit/send! channel (assoc response :body chunk) false)
            (recur))))))

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
              (if (instance? ReadPort (:body response))
                ; streaming response
                (<? (send-streaming-response response channel))
                ; basic response - send and close
                (httpkit/send! channel response true)))
            ; handler returned nil (some kind of error condition)
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
  [routes & {:keys [exception-logger exception-renderer logger]
             :or {exception-logger server-error-exception-logger
                  exception-renderer json-exception-renderer
                  logger default-logger}}]
  (-> routes
      (handle-head>)
      (normalize-response>)
      (json-response>)
      (handle-exceptions> :logger exception-logger
                          :renderer exception-renderer)
      (log-track-request> :logger logger)
      wrap-keyword-params
      wrap-nested-params
      wrap-params))


;;; ROUTING
;;; Extension to compojure routing:
;;; 1. associates endpoint (route) to response - useful for logging and
;;;    monitoring
;;; 2. async - processes route's body in a go block


; just alias Compjure's defmacro for convenience
(defmacro defroutes [name & routes]
  `(compojure/defroutes ~name ~@routes))

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
