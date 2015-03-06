(ns full.async
  (:require [clojure.core.async :refer [<! <!! >! alts! go go-loop chan] :as async]))

(defn throw-if-throwable
  "Helper method that checks if x is Throwable and if yes, wraps it in a new
  exception, passing though ex-data if any, and throws it. The wrapping is done
  to maintain a full stack trace when jumping between multiple contexts."
  [x]
  (if (instance? Throwable x)
    (throw (ex-info (.getMessage x)
                    (ex-data x)
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

(defmacro alts?
  "Same as core.async alts! but throws an exception if the channel returns a
  throwable object."
  [ports]
  `(throw-if-throwable (alts! ~ports)))

(defmacro go-try
  "Asynchronously executes the body in a go block. Returns a channel which
  will receive the result of the body when completed or an exception if one
  is thrown."
  [& body]
  `(go (try ~@body (catch Throwable e# e#))))

(defmacro go-retry
  [{:keys [exception retries delay error-fn]
    :or {error-fn nil, exception Throwable, retries 5, delay 1}} & body]
  `(let [error-fn# ~error-fn]
     (go-loop
       [retries# ~retries]
       (let [res# (try ~@body (catch Throwable e# e#))]
         (if (and (or (not error-fn#) (error-fn# res#))
                  (instance? ~exception res#)
                  (> retries# 0))
           (do
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
            results# (empty chs#)]
       (if-let [head# (first chs#)]
         (->> (<! head#)
              (conj results#)
              (recur (rest chs#)))
         results#))))

(defmacro <?*
  "Takes one result from each channel and returns them as a collection.
  The results maintain the order of channels. Throws if any of the
  channels returns an exception."
  [chs]
  `(let [chs# ~chs]
     (loop [chs# chs#
            results# (empty chs#)]
       (if-let [head# (first chs#)]
         (->> (<? head#)
              (conj results#)
              (recur (rest chs#)))
         results#))))

(defn <!!*
  [chs]
  (<!! (go (<!* chs))))

(defn <??*
  [chs]
  (<?? (go-try (<?* chs))))

(defn pmap>>
  "Takes objects from ch, asynchrously applies function f> (function should
  return channel), takes the result from the returned channel and if it's not
  nil, puts it in the results channel. Returns the results channel. Closes the
  returned channel when the input channel has been completely consumed and all
  objects have been processed."
  [f> parallelism ch]
  (let [results (chan)
        threads (atom parallelism)]
    (dotimes [_ parallelism]
      (go-loop []
        (when-let [x (<! ch)]
          (if (instance? Throwable x)
            (do
              (>! results x)
              (async/close! results))
            (do
              (when-let [result (<! (f> x))]
                (>! results result))
              (recur)))))
        (when (zero? (swap! threads dec))
          (async/close! results)))
    results))
