(ns full.async
  (:require #?(:clj [clojure.core.async :as async
                     :refer [<! <!! >! go go-loop chan]]
               :cljs [cljs.core.async :as async
                      :refer [>! <! chan]])
            #?(:clj [full.async.env :refer [if-cljs]]))
  #?(:clj (:import (clojure.core.async.impl.protocols ReadPort)))
  #?(:cljs (:require-macros
             [cljs.core.async.macros :refer [go go-loop]]
             [full.async.env :refer [if-cljs]])))

#?(:clj (do
          (defn exception? [x]
            (instance? Throwable x))

          (defn throw-if-throwable
          "Helper method that checks if x is Throwable and if yes, wraps it in a new
          exception, passing though ex-data if any, and throws it. The wrapping is done
          to maintain a full stack trace when jumping between multiple contexts."
          [x]
          (if (exception? x)
            (throw (ex-info (or (.getMessage x) (str x))
                            (or (ex-data x) {})
                            x))
            x)))

   :cljs (do
           (defn exception? [x]
             (instance? js/Error x))

           (defn throw-if-throwable
             "Helper method that checks if x is JavaScript Error. If it is, throws it,
             otherwise returns x."
             [x]
             (if (exception? x)
               (throw x)
               x))))


;; SHARED MACROS


#?(:clj
   (do
     (defmacro <?
       "Same as core.async <! but throws an exception if the channel returns a
       throwable object. Also will not crash if channel is nil."
       [ch]
       `(full.async/throw-if-throwable
          (let [ch# ~ch]
            (when ch#
              (if-cljs
                (cljs.core.async/<! ch#)
                (<! ch#))))))

     (defmacro alts?
       "Same as core.async alts! but throws an exception if the channel returns a
       throwable object."
       [ports]
       `(let [[val# port#] (if-cljs
                             (cljs.core.async/alts! ~ports)
                             (async/alts! ~ports))]
          [(full.async/throw-if-throwable val#) port#]))

     (defmacro go-try
       "Asynchronously executes the body in a go block. Returns a channel which
       will receive the result of the body when completed or an exception if one
       is thrown."
       [& body]
       `(if-cljs
          (cljs.core.async.macros/go (try ~@body (catch js/Error e# e#)))
          (go (try ~@body (catch Throwable e# e#)))))

     (defmacro go-retry
         "Attempts to evaluate a go block and retries it if `should-retry-fn`
          which is invoked with block's evaluation result evaluates to false.
          `should-retry-fn` is optional and by default it will simply check if
          result is of type Throwable (clj) / js/Error (cljs). If the evaluation
          still fails after given retries, the last failed result will be
          returned in channel.
          Parameters:
          * retries - how many times to retry (default 5 times)
          * delay - how long to wait in seconds between retries (default 1s)
          * should-retry-fn - function that is invoked with result of block's
                              evaluation and should indicate whether to retry
                              (if it returns true) or not (returns false)
          * error-fn - DEPRECATED, use should-retry-fn instead"
       [{:keys [exception retries delay error-fn should-retry-fn on-error]
         :or {error-fn nil, retries 5, delay 1, on-error nil}}
        & body]
       `(let [error-fn# ~error-fn
              on-error# ~on-error
              retry?# (or ~should-retry-fn
                          (fn [res#]
                            (and (instance? (if-cljs
                                              js/Error
                                              (or ~exception Throwable))
                                            res#)
                                 (or (not error-fn#) (error-fn# res#)))))]
          (if-cljs
            (cljs.core.async.macros/go-loop
              [retries# ~retries]
              (let [res# (try ~@body (catch :default e# e#))]
                (if (and (retry?# res#)
                         (> retries# 0))
                  (do
                    (when on-error# (on-error# res#))
                    (cljs.core.async/<! (cljs.core.async/timeout (* ~delay 1000)))
                    (recur (dec retries#)))
                  res#)))
            (go-loop
              [retries# ~retries]
              (let [res# (try ~@body (catch Throwable e# e#))]
                (if (and (retry?# res#)
                         (> retries# 0))
                  (do
                    (when on-error# (on-error# res#))
                    (<! (async/timeout (* ~delay 1000)))
                    (recur (dec retries#)))
                  res#))))))

     (defmacro <<!
       "Takes multiple results from a channel and returns them as a vector.
       The input channel must be closed."
       [ch]
       `(let [ch# ~ch]
          (if-cljs
            (cljs.core.async/<! (cljs.core.async/into [] ch#))
            (<! (async/into [] ch#)))))

     (defmacro <<?
       "Takes multiple results from a channel and returns them as a vector.
       Throws if any result is an exception."
       [ch]
       `(->> (full.async/<<! ~ch)
             (map full.async/throw-if-throwable)
             ; doall to check for throwables right away
             (doall)))

     (defmacro <!*
       "Takes one result from each channel and returns them as a collection.
       The results maintain the order of channels."
       [chs]
       `(let [chs# ~chs]
          (loop [chs# chs#
                 results# (if-cljs
                            []
                            (clojure.lang.PersistentQueue/EMPTY))]
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
                 results# (if-cljs
                            []
                            (clojure.lang.PersistentQueue/EMPTY))]
            (if-let [head# (first chs#)]
              (->> (<? head#)
                   (conj results#)
                   (recur (rest chs#)))
              (vec results#)))))))


;; CLOJURE ONLY


#?(:clj
   (do
     (defn <??
       "Same as core.async <!! but throws an exception if the channel returns a
       throwable object. Also will not crash if channel is nil."
       [ch]
       (throw-if-throwable (when ch (<!! ch))))

     (defmacro thread-try
       "Asynchronously executes the body in a thread. Returns a channel which
       will receive the result of the body when completed or an exception if one
       is thrown."
       [& body]
       `(async/thread (try ~@body (catch Throwable e# e#))))

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

     (defn <!!*
       [chs]
       (<!! (go (<!* chs))))

     (defn <??*
       [chs]
       (<?? (go-try (<?* chs))))))

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
          (and (integer? parallelism) (pos? parallelism))]}
   (let [threads (atom parallelism)]
     (dotimes [_ parallelism]
       (go
         (loop []
           (when-let [x (<! in-ch)]
             (if (exception? x)
               (do
                 (>! out-ch x)
                 (async/close! out-ch))
               (do
                 (when-let [result (<! (f> x))]
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
        (if (exception? x)
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
    (go-loop [cs cs]
      (if-let [c (first cs)]
        (if-let [v (<! c)]
          (do
            (>! out v)
            (recur cs))
          ; channel empty - move to next channel
          (recur (rest cs)))
        ; no more channels remaining - close output channel
        (async/close! out)))
    out))

(defn partition-all>>
  [n in-ch & {:keys [out-ch]}]
  "Batches results from input channel into vectors of n size and supplies
  them to ouput channel. If any input result is an exception, it is put onto
  output channel directly and ouput channel is closed."
  {:pre [(pos? n)]}
  (let [out-ch (or out-ch (chan))]
    (go-loop [batch []]
      (if-let [obj (<! in-ch)]
        (if (exception? obj)
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

(defn debounce>>
  "Debounces channel. Forwards first item from input channel to output
  immediately. After that one item every interval ms (if any). If there are more
  items in between, they are dropped."
  [ch interval]
  (let [out (chan)]
    (go-loop [last-val nil]
      (let [val (or last-val (<! ch))
            timer (async/timeout interval)]
        (if (nil? val)
          (async/close! out)
          (let [[new-val ch] (async/alts! [ch timer])]
            (condp = ch
              timer (do (>! out val) (recur nil))
              ch (recur new-val))))))
    out))
