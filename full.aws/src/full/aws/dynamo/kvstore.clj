(ns full.aws.dynamo.kvstore
  (:require [full.async :refer :all]
            [full.core.config :refer [defconfig defoptconfig]]
            [taoensso.faraday :as far]
            [full.core.log :as log])
  (:import (com.amazonaws.services.dynamodbv2.model ResourceInUseException)))

(defconfig table-name :kvstore :dynamo-table)
(defoptconfig read-throughput :kvstore :dynamo-throughput :read)
(defoptconfig write-throughput :kvstore :dynamo-throughput :write)

(def client-opts (delay
                   (let [opts {}]
                     (try
                       (far/create-table
                         opts @table-name
                         [:key :s]
                         {:throughput {:read (or @read-throughput 1)
                                       :write (or @write-throughput 1)}
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
