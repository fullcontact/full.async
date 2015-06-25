(ns full.aws.core
  (:require [full.core.config :refer [opt]])
  (:import (com.amazonaws.regions Region Regions)
           (com.amazonaws.auth AWSCredentialsProvider
                               BasicAWSCredentials
                               DefaultAWSCredentialsProviderChain)))

(def region-name   (opt [:aws :region]))
(def client-id     (opt [:aws :client-id] :default nil))
(def client-secret (opt [:aws :client-secret] :default nil))

(def region (delay (Region/getRegion (Regions/fromName @region-name))))

(defn- config-credentials-provider []
  (reify AWSCredentialsProvider
    (getCredentials [_]
      (BasicAWSCredentials. @client-id @client-secret))))

(def credentials-provider (delay (if (and @client-id @client-secret)
                                   (config-credentials-provider)
                                   (DefaultAWSCredentialsProviderChain.))))


