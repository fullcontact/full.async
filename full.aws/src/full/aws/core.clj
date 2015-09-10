(ns full.aws.core
  (:require [full.core.config :refer [opt]])
  (:import (com.amazonaws ClientConfiguration Protocol)
           (com.amazonaws.regions Region Regions)
           (com.amazonaws.services.s3 AmazonS3Client)
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

(def http-protocol-client-config
  (delay (-> (ClientConfiguration.) (.withProtocol (Protocol/HTTP)))))

(def default-https-client
  (delay (doto (AmazonS3Client. @credentials-provider) (.setRegion @region))))

(def default-http-client
  (delay (doto (AmazonS3Client. @credentials-provider @http-protocol-client-config) (.setRegion @region))))
