(ns full.async
  (:require [clojure.core.async :refer [<! <!! >! alts! go go-loop chan thread]
             :as async])
  (:import (clojure.core.async.impl.protocols ReadPort)))

(defn throw-if-throwable
  "Helper method that checks if x is Throwable and if yes, wraps it in a new
  exception, passing though ex-data if any, and throws it. The wrapping is done
  to maintain a full stack trace when jumping between multiple contexts."
  [x]
  (if (instance? Throwable x)
    (throw (ex-info (or (.getMessage x) (str x))
                    (or (ex-data x) {})
                    x))
    x))

(defmacro <?
  "Same as core.async <! but throws an exception if the channel returns a
  throwable object. Also will not crash if channel is nil."
  [ch]
  `(throw-if-throwable (let [ch# ~ch] (when ch# (<! ch#)))))

(defn <??
  "Same as core.async <!! but throws an exception if the channel returns a
  throwable object. Also will not crash if channel is nil."
  [ch]
  (throw-if-throwable (when ch (<!! ch))))

(defmacro try<?
  [ch & body]
  `(try (<? ~ch) ~@body))

(defmacro try<??
  [ch & body]
  `(try (<?? ~ch) ~@body))

(defmacro alts?
  "Same as core.async alts! but throws an exception if the channel returns a
  throwable object."
  [ports]
  `(let [[val# port#] (alts! ~ports)]
     [(throw-if-throwable val#) port#]))

(defmacro go-try
  "Asynchronously executes the body in a go block. Returns a channel which
  will receive the result of the body when completed or an exception if one
  is thrown."
  [& body]
  `(go (try ~@body (catch Throwable e# e#))))

(defmacro thread-try
  "Asynchronously executes the body in a thread. Returns a channel which
  will receive the result of the body when completed or an exception if one
  is thrown."
  [& body]
  `(thread (try ~@body (catch Throwable e# e#))))

(defmacro go-retry
  "Attempts to execute a go block and retries it if it fails. Retry conditions
   can be set via error-fn function, on-error can be used for side-effects
   after a failure occurs."
  [{:keys [exception retries delay error-fn on-error]
    :or {error-fn nil exception Throwable retries 5 delay 1 on-error nil}}
   & body]
  `(let [error-fn# ~error-fn on-error# ~on-error]
     (go-loop
       [retries# ~retries]
       (let [res# (try ~@body (catch Throwable e# e#))]
         (if (and (instance? ~exception res#)
                  (or (not error-fn#) (error-fn# res#))
                  (> retries# 0))
           (do
             (when on-error# (on-error# res#))
             (<! (async/timeout (* ~delay 1000)))
             (recur (dec retries#)))
           res#)))))

(defmacro <<!
  "Takes multiple results from a channel and returns them as a vector.
  The input channel must be closed."
  [ch]
  `(let [ch# ~ch]
     (<! (async/into [] ch#))))

(defmacro <<?
  "Takes multiple results from a channel and returns them as a vector.
  Throws if any result is an exception."
  [ch]
  `(->> (<<! ~ch)
        (map throw-if-throwable)
        ; doall to check for throwables right away
        (doall)))

(defn <<!!
  [ch]
  (lazy-seq
    (let [next (<!! ch)]
      (when next
        (cons next (<<!! ch))))))

(defn <<??
  [ch]
  (lazy-seq
    (let [next (<?? ch)]
      (when next
        (cons next (<<?? ch))))))

(defmacro <!*
  "Takes one result from each channel and returns them as a collection.
  The results maintain the order of channels."
  [chs]
  `(let [chs# ~chs]
     (loop [chs# chs#
            results# (clojure.lang.PersistentQueue/EMPTY)]
       (if-let [head# (first chs#)]
         (->> (<! head#)
              (conj results#)
              (recur (rest chs#)))
         (vec results#)))))

(defmacro <?*
  "Takes one result from each channel and returns them as a collection.
  The results maintain the order of channels. Throws if any of the
  channels returns an exception."
  [chs]
  `(let [chs# ~chs]
     (loop [chs# chs#
            results# (clojure.lang.PersistentQueue/EMPTY)]
       (if-let [head# (first chs#)]
         (->> (<? head#)
              (conj results#)
              (recur (rest chs#)))
         (vec results#)))))

(defn <!!*
  [chs]
  (<!! (go (<!* chs))))

(defn <??*
  [chs]
  (<?? (go-try (<?* chs))))

(defn pmap>>
  "Takes objects from in-ch, asynchrously applies function f> (function should
  return a channel), takes the result from the returned channel and if it's
  truthy, puts it in the out-ch. Returns the closed out-ch. Closes the
  returned channel when the input channel has been completely consumed and all
  objects have been processed.
  If out-ch is not provided, an unbuffered one will be used."
  ([f> parallelism in-ch]
   (pmap>> f> parallelism (async/chan) in-ch))
  ([f> parallelism out-ch in-ch]
   {:pre [(fn? f>)
          (and (integer? parallelism) (pos? parallelism))
          (instance? ReadPort in-ch)]}
   (let [threads (atom parallelism)]
     (dotimes [_ parallelism]
       (go
         (loop []
           (when-let [obj (<! in-ch)]
             (if (instance? Throwable obj)
               (do
                 (>! out-ch obj)
                 (async/close! out-ch))
               (do
                 (when-let [result (<! (f> obj))]
                   (>! out-ch result))
                 (recur)))))
         (when (zero? (swap! threads dec))
           (async/close! out-ch))))
     out-ch)))

(defn engulf
  "Similiar to dorun. Simply takes messages from channels but does nothing with
  them. Returns channel that will close when all messages have been consumed."
  [& cs]
  (let [ch (async/merge cs)]
    (go-loop []
      (when (<! ch) (recur)))))

(defn reduce>
  "Performs a reduce on objects from ch with the function f> (which should return
  a channel). Returns a channel with the resulting value."
  [f> acc ch]
  (let [result (chan)]
    (go-loop [acc acc]
      (if-let [x (<! ch)]
        (if (instance? Throwable x)
          (do
            (>! result x)
            (async/close! result))
          (->> (f> acc x) <! recur))
        (do
          (>! result acc)
          (async/close! result))))
    result))

(defn concat>>
  "Concatenates two or more channels. First takes all values from first channel
  and supplies to output channel, then takes all values from second channel and
  so on. Similiar to core.async/merge but maintains the order of values."
  [& cs]
  (let [out (chan)]
    (go
      (loop [cs cs]
        (if-let [c (first cs)]
          (if-let [v (<! c)]
            (do
              (>! out v)
              (recur cs))
            ; channel empty - move to next channel
            (recur (rest cs)))
          ; no more channels remaining - close output channel
          (async/close! out))))
    out))

(defn ^:deprecated partition-all>> [n in-ch & {:keys [out-ch]}]
  "Batches results from input channel into vectors of n size and supplies
  them to ouput channel. If any input result is an exception, it is put onto
  output channel directly and ouput channel is closed."
  {:pre [(pos? n)]}
  (let [out-ch (or out-ch (chan))]
    (go-loop [batch []]
      (if-let [obj (<! in-ch)]
        (if (instance? Exception obj)
          ; exception - put on output and close
          (do (>! out-ch obj)
              (async/close! out-ch))
          ; add object to current batch
          (let [new-batch (conj batch obj)]
            (if (= n (count new-batch))
              ; batch size reached - put batch on output and start a new one
              (do
                (>! out-ch new-batch)
                (recur []))
              ; process next object
              (recur new-batch))))
        ; no more results - put outstanding batch onto output and close
        (do
          (when (not-empty batch)
            (>! out-ch batch))
          (async/close! out-ch))))
    out-ch))

(defn count>
  "Counts items in a channel. Returns a channel with the item count."
  [ch]
  (async/reduce (fn [acc _] (inc acc)) 0 ch))
