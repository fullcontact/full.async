; simple Rabbit message processor that distributes
; messages equally across 1 or more output queues


(ns full.rabbit.robin
  (:require [langohr.consumers :as lc]
            [langohr.basic :as lb]
            [full.core.sugar :refer :all]
            [full.core.log :as log]
            [full.core.config :refer [defconfig]]
            [full.metrics :refer [wrap-timeit]]
            [full.rabbit :as rabbit]))

(defconfig in-queue :round-robin :in-queue)
(defconfig out-queues :round-robin :out-queues)
(defconfig prefetch :round-robin :prefetch)
(defconfig workers :round-robin :workers)

(def out (delay (vec @out-queues)))


(defn on-message
  [publish-ch ch {:keys [delivery-tag headers routing-key]} payload]
  (try
    (let [out @out
          i (rand-int (count out))
          queue (get out i)]
      (log/debug "Moving message" routing-key "to" queue)
      (lb/publish publish-ch "" queue payload {:content-type "application/json"
                                               :headers headers}))
    (lb/ack ch delivery-tag)
    (catch Exception ex
      (log/error ex (str (.getMessage ex)))
      (lb/reject ch delivery-tag))))

(def on-message (wrap-timeit "rabbit.robin" on-message))


;;; message consumer - startup ;;;


(defn subscribe
  [n]
  (let [publish-ch (rabbit/open-channel @prefetch)]
    (-> (rabbit/open-channel @prefetch)
        (lc/subscribe @in-queue (partial on-message publish-ch) {:auto-ack false}))))


(defn start
  ([] (start @workers))
  ([workers]
   (when (and (< 0 @prefetch) (< 0 workers))
     (->> (range workers)
          (pmap subscribe)
          (dorun)))))
