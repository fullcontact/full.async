(ns full.rabbit
  (:require [clojure.core.async :as async]
            [clojure.core.cache :as cache]
            [langohr.core :as lc]
            [langohr.basic :as lb]
            [langohr.channel :as lch]
            [langohr.queue :as lq]
            [langohr.conversion :as lconv]
            [full.core.sugar :refer :all]
            [full.core.log :as log]
            [full.core.config :refer [opt]]
            [full.json :refer [read-json write-json]]
            [full.metrics :refer [wrap-timeit go-try-timeit track]]
            [full.async :refer [<? go-try alts?]])
  (:import (com.rabbitmq.client QueueingConsumer)
           (com.rabbitmq.client Channel)))

(def hosts (opt [:rabbit :hosts]))

(def conn (delay (let [c (lc/connect {:hosts @hosts})]
                   (log/info "Connected to RabbitMQ servers" @hosts)
                   c)))

(defn parse-payload [payload]
  (-> payload (String. "UTF-8") (read-json)))

(defn open-channel
  ([] (open-channel 1))
  ([prefetch]
   (let [ch (lch/open @conn)]
     (lb/qos ch prefetch)
     ch)))

(def ack lb/ack)

(defn next-message
  "Fetches single message (delivery) from channel."
  ([^Channel ch, ^String, queue]
   (next-message ch queue {}))
  ([^Channel ch, ^String queue, options]
   (let [consumer (QueueingConsumer. ch)
         consumer-tag (apply lb/consume ch queue consumer options)]
     (try
       (let [delivery (.nextDelivery consumer)]
         (-> delivery
             (lconv/to-message-metadata)
             (assoc :payload (.getBody delivery))))
       (finally
         (.basicCancel ch consumer-tag))))))

(defn declarebind-permament-queue
  [ch queue exchange & {:keys [routing-key args]
                        :or {routing-key ""}}]
  (lq/declare ch queue
              {:durable true
               :auto-delete false
               :exclusive false})
  (lq/bind ch queue exchange {:routing-key routing-key :arguments args}))

(defn declare-retry-queue
  "Declares queue that will move messages to `for-queue` queue after the given interval (in secons)."
  [ch queue for-queue retry-interval]
  (lq/declare ch queue
              {:durable true
               :auto-delete false
               :arguments {"x-message-ttl" retry-interval
                           "x-dead-letter-exchange" ""
                           "x-dead-letter-routing-key" for-queue}}))

(defn error-payload
  "Helper method for publish-errors that attempts to parse and update error payload."
  [payload ex errors2-threshold]
  (try
    (-> (parse-payload payload)
        (assoc :last-error (.getMessage ex))
        (update-in [:retry] (fn [retry] (-> retry (or 0) inc)))
        (as-> event [(write-json event) (:retry event)]))
    (catch Exception _
      ; if we could not parse event, lets just put message directly in errors-park
      [payload (inc errors2-threshold)])))

(defn publish-error
  "Publishes message to error queue attaching error and retry count (incrementing it).
  If retry count exceeds threshold, message is published to errors2-queue, otherwise
  to errors-queue. Message is expexted to be JSON-parsable, if that's not the case the
  message will be automatically put into errors2-queue but error and retry count won't
  be attached."
  [ch payload headers ex errors-queue errors2-queue errors-park-queue errors-threshold errors2-threshold]
  (let [[payload retry] (error-payload payload ex errors2-threshold)
        queue (cond
                (> retry errors2-threshold) errors-park-queue
                (> retry errors-threshold) errors2-queue
                :else errors-queue)]
    (lb/publish ch "" queue payload {:headers headers})))

(def publish-error (wrap-timeit "rabbit.publish-error" publish-error))

(def message-count-fetch-interval 5000)

(def message-count-cache (atom (cache/ttl-cache-factory {} :ttl message-count-fetch-interval)))

(defn message-count>
  "Same as langohr.queue/message-count but with timeout.
  langohr.queue/message-count sometimes appears to block and hang forever."
  ([ch queue] (message-count> ch queue message-count-fetch-interval))
  ([ch queue timeout]
   (if-let [c (cache/lookup @message-count-cache queue)]
     (go-try c)
     (let [c (go-try (lq/message-count ch queue))]
       (go-try-timeit
         "rabbit.message-count"
         (let [[v _] (alts? [c (async/timeout timeout)])]
           (swap! message-count-cache #(cache/miss % queue v))
           v))))))

(defn limit-message-count>
  "Checks if queue is below the given message count. Waits for the count to fall if it isn't."
  [ch queue below]
  (async/go-loop []
                 (let [messages (<? (message-count> ch queue))]
                   (when (> messages below)
                     (log/debug "Queue" queue "over limit" messages ". Waiting " message-count-fetch-interval " ms")
                     (<? (async/timeout message-count-fetch-interval))
                     (recur)))))

(defn monitor-queue-size
  "Periodically pools queue size (number of pending messages) and submits a metric to Riemann."
  [ch queue]
  (async/go-loop []
                 (when-let [cnt (<? (message-count> ch queue))]
                   (track {:service (str "rabbit." queue ".messages") :metric cnt}))
                 (<? (async/timeout message-count-fetch-interval))
                 (recur)))

(defn- move-message [n ch consumer to-queue]
  (let [delivery (.nextDelivery consumer)
        message (-> delivery
                    (lconv/to-message-metadata)
                    (assoc :payload (.getBody delivery)))]
    (when (= 0 (mod n 100))
      (log/info "Moving message" (:delivery-tag message)))
    (lb/publish ch "" to-queue (:payload message) {:headers (:headers message)})
    (lb/ack ch (:delivery-tag message))))

(defn move-messages [n from-queue to-queue]
  (with-open [ch (open-channel)]
    (let [consumer (QueueingConsumer. ch)
          consumer-tag (apply lb/consume ch from-queue consumer {})]
      (->> (range n)
           (pmap (fn [n] (move-message n ch consumer to-queue)))
           (dorun))
      (.basicCancel ch consumer-tag))))
