(ns full.aws.dynamo.kvstore
  (:require [full.async :refer :all]
            [full.core.config :refer [opt]]
            [taoensso.faraday :as far]
            [full.core.log :as log])
  (:import (com.amazonaws.services.dynamodbv2.model ResourceInUseException)))

(def table-name       (opt [:kvstore :dynamo-table]))
(def read-throughput  (opt [:kvstore :dynamo-throughput :read] :default 1))
(def write-throughput (opt [:kvstore :dynamo-throughput :write] :default 1))

(def client-opts (delay
                   (let [opts {}]
                     (try
                       (far/create-table
                         opts @table-name
                         [:key :s]
                         {:throughput {:read @read-throughput
                                       :write @write-throughput}
                          :block? true})
                       (catch ResourceInUseException _
                         ; table already exists
                         ))
                     opts)))

(defn put> [key value]
  (thread-try
    (far/put-item @client-opts @table-name
                  {:key (str key)
                   :value (far/freeze value)})
    value))

(defn get> [key]
  (thread-try
    (-> (far/get-item @client-opts @table-name {:key (str key)})
        :value)))

(defn delete> [key]
  (thread-try
    (far/delete-item @client-opts @table-name {:key (str key)})
    true))
