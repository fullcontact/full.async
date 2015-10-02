(ns full.t-metrics
  (:require [midje.sweet :refer :all]
            [full.metrics :refer :all]))

(facts
  "Test batch client returned when size specified."
  (fact (-> (get-client {:protocol "udp" :config {:host "127.0.0.1"} :batch-size 10})
            (type)
            (str)) => "class com.aphyr.riemann.client.RiemannBatchClient")

  (fact (-> (get-client {:protocol "udp" :config {:host "127.0.0.1"}})
            (type)
            (str)) => "class com.aphyr.riemann.client.RiemannClient"))
