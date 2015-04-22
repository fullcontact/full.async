(ns full.cache
  (:require [clojurewerkz.spyglass.client :as spyglass]
            [taoensso.nippy :as nippy]
            [full.core.log :as log]
            [full.core.config :refer [defoptconfig]]
            [full.async :refer [go-try]]
            [clojure.core.async :refer [>! <! chan mult tap close!]])
  (:import (java.util.logging Logger Level)
           (net.jodah.expiringmap ExpiringMap ExpiringMap$ExpirationPolicy)
           (java.util.concurrent TimeUnit)
           (clojure.core.async.impl.protocols ReadPort))
  (:refer-clojure :exclude [set, get]))


(defoptconfig memcache-address :memcache-address)


;;; ASYNC LOADING SUPPORT


(defn- get-or-create-state [states k]
  ; we cannot do loading directly in atom's swap function as it can be invoked multiple times with the same state
  ; hence we add :first? attribute that gets reset to false on every subsequent invocation
  (if-let [state (states k)]
    (assoc states k (assoc state :first? false))
    (let [in-chan (chan)
          ; we need multiplexer as there can be multiple requests for the same key while loader is in progress
          ; and we want to distribute same value from loader to all requests
          mult (mult in-chan)]
      (assoc states k {:in-chan in-chan
                       :mult mult
                       :first? true}))))

(defn- do-get-or-load>
  [k loader> timeout {:keys [setf getf states]}]
  (go-try
    (or (getf k)
        ; value missing - load it
        (let [new-states (swap! states get-or-create-state k)
              state (new-states k)]
          (if (:first? state)
            ; first invocation - load state and publish to subsequent waiting invocations
            (let [in-chan (:in-chan state)
                  r (try (loader>) (catch Exception e e))
                  v (if (instance? ReadPort r)
                      (<! r)
                      r)]
              (when (and v (not (instance? Throwable v)))
                (setf k v timeout))
              (swap! states dissoc k)
              (when v
                (>! in-chan v))
              (close! in-chan)
              v)
            ; second+ invocation - wait for first invocation to load the results and publish
            ; via multiplexer
            (let [out-chan (chan)]
              (tap (:mult state) out-chan)
              (<! out-chan)))))))


;;; REMOTE CACHE


(defn- blackhole-memcache-logging []
  (System/setProperty "net.spy.log.LoggerImpl" "net.spy.memcached.compat.log.SunLogger")
  (.setLevel (Logger/getLogger "net.spy.memcached") Level/SEVERE))

(def ^:private con
  (delay
    (when @memcache-address
      (blackhole-memcache-logging)
      (let [c (spyglass/bin-connection @memcache-address)]
        (.addShutdownHook (Runtime/getRuntime) (Thread. #(spyglass/shutdown c)))
               c))))

(defn connected? []
  @con)

(defn rget
  [k & {:keys [throw?]}]
  (let [v (try
            (spyglass/get @con k)
            (catch Exception e
              (if throw?
                (throw e)
                (log/warn k "not retrieved from cache due to" e))))]
    (if v
      (log/debug "Cache hit:" k)
      (log/debug "Cache miss:" k))
    (if v
      (nippy/thaw v)
      v)))

(defn rset
  ([k v] (rset k v 0))
  ([k v timeout & {:keys [throw?]}]
   (try
     (spyglass/set @con k timeout (nippy/freeze v))
     (log/debug "Added to cache:" k)
     (catch Exception e
       (if throw?
         (throw e)
         (log/warn k "not added to cache due to" e))))
   v))

(defn radd
  ([k v] (radd k v 0))
  ([k v timeout & {:keys [throw?]}]
   (try
     (let [res @(spyglass/add @con k timeout (nippy/freeze v))]
       (if res
         (do
           (log/debug "Added to cache:" k)
           v)
         (log/debug "Already in cache:" k)))
     (catch Exception e
       (if throw?
         (throw e)
         (log/warn k "not added to cache due to" e))))))

(defn radd-or-get
  ([k v] (radd-or-get k v 0))
  ([k v timeout & {:keys [throw?]}]
   (or (radd k v timeout :throw throw?)
       (rget k :throw throw?))))

(defn rdelete
  [k & {:keys [throw?]}]
  (try
    (spyglass/delete @con k)
    (log/debug "Deleted from cache:" k)
    (catch Exception e
      (if throw?
        (throw e)
        (log/warn k "not deleted from cache due to" e)))))

(defn rget-or-load
  ([k loader] (rget-or-load k loader 0))
  ([k loader timeout & {:keys [throw?]}]
   (if-let [v (rget k)]
     v
     (let [v (loader)]
       (when v (rset k v timeout :throw? throw?))
       v))))

(def ^:private rget-or-load-states (atom {}))

(defn rget-or-load>
  "Gets value from cache or loads it via async function, ensuring there's only one loader active for given key (ie.
  it's synchronized for given key). Loader function must return core.async channel."
  ([k loader>] (rget-or-load> k loader> 0))
  ([k loader> timeout & {:keys [throw?]}]
   (do-get-or-load> k loader> timeout
                    {:getf (fn [k] (rget k :throw? throw?))
                     :setf (fn [k v timeout] (rset k v timeout :throw? throw?))
                     :states rget-or-load-states})))


;;; LOCAL CACHE


(def ^:private local-cache (-> (ExpiringMap/builder) (.variableExpiration) (.build)))

(defn lget
  [k]
  (.get local-cache k))

(defn lset
  [k v timeout]
  {:pre [(pos? timeout)]}
  (.put local-cache k v ExpiringMap$ExpirationPolicy/CREATED timeout TimeUnit/SECONDS)
  v)

(defn lget-or-load
  [k loader timeout]
  (if-let [v (lget k)]
    v
    (let [v (loader)]
      (when v (lset k v timeout))
      v)))

(defn ldelete
  [k]
  (.remove local-cache k))

(def ^:private lget-or-load-states (atom {}))

(defn lget-or-load>
  [k loader> timeout]
  (do-get-or-load> k loader> timeout
                   {:getf lget
                    :setf lset
                    :states lget-or-load-states}))


;;; 2 LEVEL CACHE (LOCAL + REMOTE)


(defn get
  "Gets value from a 2-level cache (local+memcache). If key is not in local
  cache, the remote memcache gets queried. If it does contain the key, the
  value is returned and optionally put in local cache (if timeout argument
  is specified)."
  ([k]
    (or (lget k)
        (when (connected?) (rget k :throw? false))))
  ([k timeout]
    (or (lget k)
        (when (connected?)
          (when-let [v (rget k :throw? false)]
            (lset k v timeout))))))

(defn set
  "Puts value in a 2-level cache (local+memcache)."
  [k v timeout]
  (when (connected?) (rset k v timeout :throw? false))
  (lset k v timeout))

(defn delete
  "Deletes value from a 2-level cache (local+memcache)."
  [k]
  (when (connected?) (rdelete k :throw? false))
  (ldelete k))

(defn get-or-load
  "Gets value from a 2-level cache (local+memcache). If the key is not in cache,
  loads it by calling loader function and stores the result in cache and returns
  it."
  [k loader timeout]
  (if-let [v (get k timeout)]
    v
    (let [v (loader)]
      (when v (set k v timeout))
      v)))

(def ^:private get-or-load-states (atom {}))

(defn get-or-load>
  "Asynchronous version of get-or-load. Gets value from a 2-level cache. If the
  key is not in cache,loads it by calling loader function and stores the result
  in cache and returns it. Returns core.async channel that will contain the
  value. Loader function should also return a core.async channel with loaded
  value."
  [k loader> timeout]
  (do-get-or-load> k loader> timeout
                   {:getf (fn [k] (get k timeout))
                    :setf set
                    :states get-or-load-states}))