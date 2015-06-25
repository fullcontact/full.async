; simple Rabbit message processor that distributes
; messages equally across 1 or more output queues


(ns full.rabbit.robin
  (:require [langohr.consumers :as lc]
            [langohr.basic :as lb]
            [full.core.sugar :refer :all]
            [full.core.log :as log]
            [full.core.config :refer [opt]]
            [full.metrics :refer [wrap-timeit]]
            [full.rabbit :as rabbit]))


(def in-queue   (opt [:round-robin :in-queue]))
(def out-queues (opt [:round-robin :out-queues] :mapper vec))
(def prefetch   (opt [:round-robin :prefetch]))
(def workers    (opt [:round-robin :workers]))


(defn on-message
  [publish-ch ch {:keys [delivery-tag headers routing-key]} payload]
  (try
    (let [out @out-queues
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
