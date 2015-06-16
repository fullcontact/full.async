(ns full.aws.core
  (:require [full.core.config :refer [defconfig defoptconfig]])
  (:import (com.amazonaws.regions Region Regions)
           (com.amazonaws.auth BasicAWSCredentials
                               DefaultAWSCredentialsProviderChain)))

(defconfig region-name :aws :region)

(defoptconfig client-id :aws :client-id)
(defoptconfig client-secret :aws :client-secret)


(def region (delay (Region/getRegion (Regions/fromName @region-name))))

(def credentials (delay (if (and @client-id @client-secret)
                          (BasicAWSCredentials. @client-id @client-secret)
                          (-> (DefaultAWSCredentialsProviderChain.)
                              (.getCredentials)))))
