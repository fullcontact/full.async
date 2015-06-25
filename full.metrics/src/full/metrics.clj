; full.metrics
; Monitoring metrics support via Riemann.

(ns full.metrics
  (:require [full.core.sugar :refer :all]
            [full.core.log :as log]
            [full.core.config :refer [opt]]
            [riemann.client :as rmn]
            [full.async :refer [go-try thread-try]])
  (:refer-clojure :exclude [send]))


;;; CONFIG ;;;


(def riemann-config (opt :riemann :default :nil))
(def protocol (opt [:riemann :protocol] :default "udp"))
(def tags (opt [:riemann :tags] :default nil))
(def acknowledge-by-default (opt [:riemann :acknowledge-by-default] :default false))

(def client (delay (if (= "udp" @protocol)
                     (do
                       (log/info "Connecting to Riemann server" (:host @riemann-config) "via UDP")
                       (rmn/udp-client @riemann-config))
                     (do
                       (log/info "Connecting to Riemann server" (:host @riemann-config) "via TCP")
                       (rmn/tcp-client @riemann-config)))))


;;; EVENT PUT ;;;


(defn normalize [event]
  (cond-> event
          ; if event is string convert it to {:service event}
          (string? event) (->> (hash-map :service))
          @tags (assoc :tags (concat (or (:tags event) []) @tags))))

(defn send-event [event ack]
  (when @riemann-config
    (try
      (rmn/send-event @client event ack)
      (catch Exception e
        (log/error e "error sending event" event)))))

(defn- send-events [events ack]
  (when @riemann-config
    (try
      (rmn/send-events @client events ack)
      (catch Exception e
        (log/error e "error sending events" events)))))

(defn- log-event [event]
  (-> (str "full.metrics." (:service event))
      (org.slf4j.LoggerFactory/getLogger)
      (.debug (pr-str event))))

(defn track
  "Send an event over client. Requests acknowledgement from the Riemann
   server by default. If ack is false, sends in fire-and-forget mode."
  ([event] (track event @acknowledge-by-default))
  ([event ack]
   (doto (normalize event)
     (log-event)
     (send-event (true? ack)))))

(defn track*
  ([events] (track* events @acknowledge-by-default))
  ([events ack]
   (let [events (map normalize events)]
     (doseq [event events] (log-event event))
     (send-events events (true? ack)))))


;;; EVENT PUT SUGAR ;;;


(defn wrap-event [event]
  (if (string? event)
    {:service event}
    event))

(defn gauge
  "A gauge is an instantaneous measurement of a value. For example, we may want
  to measure the number of pending jobs in a queue."
  [event value]
  (-> (wrap-event event)
      (assoc :metric value
             :tags ["gauge"])
      (track)))

(def timeit-gauges (atom {}))

(defn update-timeit-gauge [key f]
  (get (swap! timeit-gauges update-in [key] (fnil f 0)) key))

(defmacro timeit
  [event & body]
  `(let [event# ~event
         start-time# (time-bookmark)
         event# (wrap-event event#)
         g# (update-in event# [:service] str "/gauge")]
     (gauge g# (update-timeit-gauge (:service event#) inc))
     (let [res# (try ~@body (catch Throwable t# t#))
           fail# (instance? Throwable res#)]
       (track (assoc event# :metric (ellapsed-time start-time#)
                            :tags (conj (or (:tags event#) []) "timeit")
                            :state (if fail#
                                     "critical"
                                     "ok")
                            :description (when fail# (str res#))))
       (gauge g# (update-timeit-gauge (:service event#) dec))
       (if fail#
         (throw res#)
         res#))))

(defmacro go-try-timeit
  [event & body]
  `(go-try (timeit ~event ~@body)))

(defmacro thread-try-timeit
  [event & body]
  `(thread-try (timeit ~event ~@body)))

(defn increment
  [event]
  (-> (wrap-event event)
      (assoc :metric 1
             :tags ["increment"])
      (track)))

(defn wrap-timeit
  "Wraps function into `timeit`.
  Example: ((wrap-timeit event do-something) a b)"
  [event f]
  (fn
    ; several defintions to optimize performance
    ([a] (timeit event (f a)))
    ([a b] (timeit event (f a b)))
    ([a b c] (timeit event (f a b c)))
    ([a b c d & more] (timeit event (apply f (cons a (cons b (cons c (cons d more)))))))))


;;; EVENT GET ;;;


(defn query
  "Query the server for events in the index. Returns a list of events."
  [string]
  {:pre [@riemann-config]}
  (rmn/query @client string))
