(ns full.aws.sqs
  (:require [clojure.core.async :refer [go go-loop <! >! chan timeout
                                        onto-chan alts!]]
            [clojure.string :as strs]
            [full.core.config :refer [opt]]
            [full.async :refer :all]
            [full.core.sugar :refer :all]
            [full.core.log :as log]
            [full.edn :refer [read-edn write-edn]]
            [full.json :refer [read-json write-json]]
            [full.aws.core :as aws]
            [full.metrics :refer [thread-try-timeit timeit] :as metrics]
            [camelsnake.core :refer :all]
            [full.time :refer :all]
            [clj-time.core :as t])
  (:import (com.amazonaws.services.sqs AmazonSQSAsyncClient)
           (com.amazonaws.services.sqs.buffered AmazonSQSBufferedAsyncClient)
           (com.amazonaws.services.sqs.model SendMessageRequest
                                             ReceiveMessageRequest
                                             DeleteMessageRequest
                                             ChangeMessageVisibilityRequest
                                             CreateQueueRequest
                                             SetQueueAttributesRequest
                                             DeleteQueueRequest
                                             Message
                                             GetQueueAttributesRequest
                                             ListQueuesRequest SendMessageBatchRequest SendMessageBatchRequestEntry BatchResultErrorEntry)))


(def message-count-fetch-interval (opt [:aws :sqs :message-count-fetch-interval] :default nil))

(def max-wait-time 20)
(def max-max-messages 10)
(def default-visibility-timeout 30)
(def default-extend-visibility-after 20)
(def max-visibility-timeout 43200)


(def client (delay (-> @aws/credentials-provider
                       (AmazonSQSAsyncClient.)
                       (AmazonSQSBufferedAsyncClient.))))


;;; QUEUES


(defn str-or-nil [value]
  (if (nil? value)
    nil
    (str value)))

(defn- queue-attributes
  [{:keys [delay-seconds
           maximum-message-size
           message-retention-period
           policy
           receive-message-wait-time
           visibility-timeout
           redrive-policy]}]
  (?hash-map
    "DelaySeconds" (str-or-nil delay-seconds)
    "MaximumMessageSize" (str-or-nil maximum-message-size)
    "MessageRetentionPeriod" (str-or-nil message-retention-period)
    "Policy" (str-or-nil policy)
    "ReceiveMessageWaitTimeSeconds" (str-or-nil receive-message-wait-time)
    "VisibilityTimeout" (str-or-nil visibility-timeout)
    "RedrivePolicy" (when redrive-policy (write-json redrive-policy))))

(defn create-queue>
  [queue-name attributes]
  (thread-try
    (let [attributes (queue-attributes attributes)]
      (log/info "Creating queue" queue-name "with attributes" attributes)
      (-> (CreateQueueRequest. queue-name)
          (.withAttributes attributes)
          (->> (.createQueue @client))
          .getQueueUrl))))

(defn delete-queue>
  [queue-name]
  (thread-try
    (log/info "Deleting queue" queue-name)
    (->> (DeleteQueueRequest. queue-name)
         (.deleteQueue @client))))

(defn set-queue-attributes>
  [queue-url attributes]
  (thread-try
    (->> (SetQueueAttributesRequest. queue-url (queue-attributes attributes))
         (.setQueueAttributes @client))))

(defn- try-update
  [m k f]
  (let [v (try
            (-> (get m k)
                (f))
            ; exception -> nil
            (catch Exception _))]
    (if (nil? v)
      (dissoc m k)
      (assoc m k v))))

(defn parse-int [v] (Integer/parseInt v))
(defn parse-ts [v] (dt<-epoch (* 1000 (Long/parseLong v))))
(defn parse-long-ts [v] (dt<-epoch (Long/parseLong v)))

(defn- parse-queue-attributes [attributes]
  (-> attributes
      (->keyword-keys)
      (try-update :approximate-number-of-messages parse-int)
      (try-update :approximate-number-of-messages-delayed parse-int)
      (try-update :approximate-number-of-messages-not-visible parse-int)
      (try-update :delay-seconds parse-int)
      (try-update :visibility-timeout parse-int)
      (try-update :maximum-message-size parse-int)
      (try-update :created-timestamp parse-ts)
      (try-update :last-modified-timestamp parse-ts)
      (try-update :message-retention-period parse-int)
      (try-update :redrive-policy read-json)
      (try-update :receive-message-wait-time-seconds parse-int)))

(defn get-queue-attributes>
  [queue-url]
  (thread-try
    (->> (GetQueueAttributesRequest. queue-url ["All"])
         (.getQueueAttributes @client)
         (.getAttributes)
         (parse-queue-attributes))))

(defn set-dead-letter-queue>
  [source-queue-url dead-letter-queue-url & {:keys [max-receive-count]
                                             :or {max-receive-count 5}}]
  (go-try
    (let [dead-letter-queue-arn (-> (get-queue-attributes> dead-letter-queue-url)
                                    <?
                                    :queue-arn)]
      (<? (set-queue-attributes>
            source-queue-url
            {:redrive-policy
             {:max-receive-count max-receive-count
              :dead-letter-target-arn dead-letter-queue-arn}})))))

(defn list-queues>
  [prefix]
  (thread-try
    (->> (ListQueuesRequest. prefix)
         (.listQueues @client)
         (.getQueueUrls))))

(defn monitor-queue-size
  "Periodically pools queue size and submits to Riemann."
  [queue-url]
  (let [name (-> (strs/split queue-url #"/") last)]
    (go-loop []
      (let [attrs (<! (get-queue-attributes> queue-url))]
        (when (map? attrs)
          (metrics/track* [{:service (str "sqs." name ".messages")
                            :metric (:approximate-number-of-messages attrs)}
                           {:service (str "sqs." name ".messages.delayed")
                            :metric (:approximate-number-of-messages-delayed attrs)}
                           {:service (str "sqs." name ".messages.notvis")
                            :metric (:approximate-number-of-messages-not-visible attrs)}])))
      (<! (timeout (* 1000 (or @message-count-fetch-interval 5))))
      (recur))
    nil))


;;; MESSAGES


(defn- parse-message-attributes [attributes]
  (-> attributes
      (->keyword-keys)
      (try-update :approximate-receive-count parse-int)
      (try-update :sent-timestamp parse-long-ts)
      (try-update :approximate-first-receive-timestamp parse-long-ts)))

(defn- unserialize-message [^Message m unserializer]
  (try
    (cond-> (.getBody m)
            unserializer (unserializer))
    (catch Exception e
      (throw (RuntimeException. (str "Error unserializing message " (.getBody m)
                                     ": " (.getMessage e)) e)))))

(defn- parse-message
  [^Message m queue-url becomes-visible unserializer]
  {:message-id (.getMessageId m)
   :receipt-handle (.getReceiptHandle m)
   :body (unserialize-message m unserializer)
   :attributes (parse-message-attributes (.getAttributes m))
   :queue-url queue-url
   :becomes-visible becomes-visible})

(defn send-message>
  ([message] (send-message> message {}))
  ([{:keys [queue-url body] :as message}
    {:keys [delay-seconds serializer] :or {serializer write-edn}}]
   {:pre [(string? queue-url)]}
   (thread-try-timeit
     "sqs.send"
     (-> (SendMessageRequest. queue-url
                              (if serializer
                                (serializer body)
                                (str body)))
         (cond-> delay-seconds (.withDelaySeconds (int delay-seconds)))
         (->> (.sendMessage @client))
         (.getMessageId)))))

(defn send-message-batch
  ([queue-url bodies] (send-message-batch queue-url bodies {}))
  ([queue-url bodies {:keys [serializer] :or {serializer write-edn}}]
   (timeit
     "sqs.send-batch"
     (let [entries (->> bodies
                        (map-indexed
                          (fn [i body]
                            (->> (if serializer
                                   (serializer body)
                                   (str body))
                                 (SendMessageBatchRequestEntry. (str i)))))
                        )
           res (->> (SendMessageBatchRequest. queue-url entries)
                    (.sendMessageBatch @client))]
       (if-let [failed (not-empty (.getFailed res))]
         (do
           (doseq [^BatchResultErrorEntry e failed]
             (log/error "Error sending message"
                        (nth bodies (Integer/parseInt (.getId e)))
                        ":" (.getMessage e) (str "(" (.getCode e) ")")))
           false)
         true)))))

(defn send-message-batch>
  ([queue-url bodies] (send-message-batch> queue-url bodies {}))
  ([queue-url bodies options]
    (thread-try
      (send-message-batch queue-url bodies options))))

(defn- now-plus-seconds [seconds]
  (t/plus (t/now) (t/seconds seconds)))

(defn receive-messages>
  ([queue-url] (receive-messages> queue-url {}))
  ([queue-url {:keys [wait-time max-messages visibility-timeout unserializer]
               :or {wait-time max-wait-time
                    max-messages max-max-messages
                    visibility-timeout default-visibility-timeout
                    unserializer read-edn}}]
   {:pre [(and (integer? visibility-timeout) (pos? visibility-timeout)
               (>= max-visibility-timeout visibility-timeout))
          (and (integer? max-messages) (pos? max-messages)
               (>= max-max-messages max-messages))
          (and (integer? wait-time) (pos? wait-time)
               (>= max-wait-time wait-time))]}
   (thread-try-timeit
     "sqs.receive"
     (let [start (time-bookmark)
           becomes-visible (now-plus-seconds visibility-timeout)
           messages (-> (ReceiveMessageRequest. queue-url)
                        (.withAttributeNames ["All"])
                        (cond-> wait-time (.withWaitTimeSeconds (int wait-time))
                                max-messages (.withMaxNumberOfMessages (int max-messages))
                                visibility-timeout (.withVisibilityTimeout (int visibility-timeout)))
                        (->> (.receiveMessage @client)
                             (.getMessages)
                             (map #(parse-message %
                                                  queue-url
                                                  becomes-visible
                                                  unserializer))))]
       (log/debug "Received"
                  (count messages)
                  "in" (str (ellapsed-time start) "ms")
                  "from" queue-url
                  (str "(wait time " wait-time ")"))
       messages))))

(defn receive-messages>>
  "Continously receive messages from AWS with long-polling. Returns an inifinite
   channel that will yield the received messages."
  ([queue-url] (receive-messages>> queue-url {}))
  ([queue-url {:keys [wait-time max-messages visibility-timeout unserializer]
               :or {wait-time max-wait-time
                    max-messages max-max-messages
                    visibility-timeout default-visibility-timeout
                    unserializer read-edn}}]
   {:pre [(and (integer? wait-time) (< 0 wait-time)
               (>= max-wait-time wait-time))]}
   (let [ch (chan (or max-messages max-max-messages))]
     (go-loop
       [last-delay 0]
       (let [messages (<! (receive-messages> queue-url
                                             {:wait-time wait-time
                                              :max-messages max-messages
                                              :visibility-timeout visibility-timeout
                                              :unserializer unserializer}))]
         (if (instance? Exception messages)
           ; progressively increase delay starting from 5 seconds
           (let [delay (if (pos? last-delay) (* 2 last-delay) 5)]
             (log/error (str "Error receiveing SQS messages " messages
                             ", will retry in " delay "sec"))
             (<! (timeout (* 1000 delay)))
             (recur delay))
           (do
             (<! (onto-chan ch messages false))
             (recur 0)))))
     ch)))

(defn delete-message>
  [{:keys [queue-url receipt-handle] :as message}]
  {:pre [(string? queue-url)
         (string? receipt-handle)]}
  (thread-try-timeit
    "sqs.delete"
    (->> (DeleteMessageRequest. queue-url receipt-handle)
         (.deleteMessage @client))))

(defn change-message-visibility>
  [{:keys [queue-url receipt-handle] :as message} visibility-timeout]
  {:pre [(string? queue-url)
         (string? receipt-handle)
         (integer? visibility-timeout)]}
  (thread-try-timeit
    "sqs.change-vis"
    (->> (ChangeMessageVisibilityRequest. queue-url
                                          receipt-handle
                                          (int visibility-timeout))
         (.changeMessageVisibility @client))
    (assoc message :becomes-visible (now-plus-seconds visibility-timeout))))

(defn- retry-message>
  [message retries]
  (go
    (if retries
      (let [receive-count (:approximate-receive-count (:attributes message))
            delay (or (get retries (dec receive-count))
                      (last retries))
            ; distribute the messages over a time period
            delay (int (+ (/ delay 2) (rand-int delay)))]
        ; Simply make message invisible for the given time.
        (<! (change-message-visibility> message delay))
        (str "will retry in " delay "sec "
             "(" receive-count ")"))
      ; else
      "not retrying")))

(defn- handle-message>
  [{:keys [message handler> visibility-timeout extend-visibility-after
           retries auto-delete]}]
  (go
    (try
      (let [handler-ch (handler> message)]
        (if extend-visibility-after
          (loop [message message
                 extension 0]
            (let [timeout-ch (timeout (* 1000 extend-visibility-after))
                  [_ port] (alts? [handler-ch timeout-ch])]
              (when (= port timeout-ch)
                (log/debug "Extending visibility for message" message
                           (str "(#" (inc extension) ")"))
                (recur (<! (change-message-visibility> message visibility-timeout))
                       (inc extension)))))
          (<? handler-ch))
        (when auto-delete
          (let [res (<! (delete-message> message))]
            (when (instance? Throwable res)
              (log/error "Error deleting message" message res)))))
      (catch Throwable ex
        (let [res (<! (retry-message> message retries))]
          (log/error ex (str "Error processing message " message ", " res)))))))

(defn subscribe
  "Repeatedly fetches messages from SQS queue and invokes handler function for
   each message with specified parallelism. Automatically retries messages with
   configurable delay.
   Parameters:
   * queue-url  SQS queue URL
   * handle>    handler function, should accept one parameter (message) and return
                channel that gets closed when message is processed. If channel
                yields exception, the message will be retried after a delay
   * :parallelism  Max number of messages to handle in parallel. Default 10.
   * :retries      Vector of retry intervals in seconds, for example [4 10] will
                   mean that first time the message will be retry after
                   4 +-2 sec, second and following times after 10 +-5 sec.
   * :visibility-timeout        Number of seconds message is not visible to
                                other consumers once received.
   * :extend-visibility-after   Number of seconds after which visibility timeout
                                is automatically extended to allow additional
                                processing time. Should be less than
                                :visibility-timeout. Set to nil to disable.
   * :auto-delete  Whether to automatically delete successfully handled
                   messages. Default is true"
  ([queue-url handler>] (subscribe queue-url handler> {}))
  ([queue-url handler> {:keys [visibility-timeout extend-visibility-after
                               unserializer parallelism retries auto-delete]
                        :or {visibility-timeout default-visibility-timeout
                             extend-visibility-after default-extend-visibility-after
                             unserializer read-edn
                             parallelism 1
                             retries [5 60 900 3600]
                             auto-delete true}}]
   {:pre [(pos? parallelism)
          (or (nil? extend-visibility-after)
              (and (pos? extend-visibility-after)
                   (pos? visibility-timeout)
                   (> visibility-timeout extend-visibility-after)))]}
   (->> (receive-messages>> queue-url {:wait-time 20
                                       :max-messages (min 10 parallelism)
                                       :visibility-timeout visibility-timeout
                                       :unserializer unserializer})
        (pmap>> #(handle-message> {:message %
                                   :handler> handler>
                                   :visibility-timeout visibility-timeout
                                   :extend-visibility-after extend-visibility-after
                                   :retries retries
                                   :auto-delete auto-delete})
                parallelism)
        (engulf))))
