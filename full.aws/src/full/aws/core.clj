(ns full.aws.core
  (:require [full.core.config :refer [defconfig defoptconfig]])
  (:import (com.amazonaws.regions Region Regions)
           (com.amazonaws.auth AWSCredentialsProvider
                               BasicAWSCredentials
                               DefaultAWSCredentialsProviderChain)))

(defconfig region-name :aws :region)

(defoptconfig client-id :aws :client-id)
(defoptconfig client-secret :aws :client-secret)

(def region (delay (Region/getRegion (Regions/fromName @region-name))))

(defn- config-credentials-provider []
  (reify AWSCredentialsProvider
    (getCredentials [_]
      (BasicAWSCredentials. @client-id @client-secret))))

(def credentials-provider (delay (if (and @client-id @client-secret)
                                   (config-credentials-provider)
                                   (DefaultAWSCredentialsProviderChain.))))


