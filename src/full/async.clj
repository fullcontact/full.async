(ns full.async
  (:require [clojure.core.async :refer [<! <!! >! alts! go go-loop chan] :as async]))


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
  "Asynchronously executes the body in a go block. Returns a channel
  which will receive the result of the body when completed. In case of
  an exception (type might be given) repeats retries times with a
  delay. An error function can be provided to cover errors
  separately. Returns the throwable if the retries fail."
  [{:keys [exception retries delay error-fn]
    :or {error-fn nil, exception Throwable, retries 5, delay 1}} & body]
  `(let [error-fn# ~error-fn]
     (go-loop
       [retries# ~retries]
       (let [res# (try ~@body (catch Throwable e# e#))]
         (if (and (instance? ~exception res#)
                  (or (not error-fn#) (error-fn# res#))
                  (> retries# 0))
           (do
             (<! (async/timeout (* ~delay 1000)))
             (recur (dec retries#)))
           res#)))))

(defmacro go-try> [err-chan & body]
  "Same as go-try, but puts errors directly on a channel and returns
  nil on the resulting channel."
  `(go (try
         ~@body
         (catch Throwable e#
           (>! ~err-chan e#)))))

(defmacro go-loop-try> [err-chan bindings & body]
  "Put throwables arising in the go-loop on an error channel."
  `(go (try
         (loop ~bindings
           ~@body)
         (catch Throwable e#
           (>! ~err-chan e#)))))

(defmacro go-loop-try [bindings & body]
  "Returns result of the loop or a throwable in case of an exception."
  `(go-try (loop ~bindings ~@body) ))

(defmacro <<!
  "Takes multiple results from a channel and returns them as a vector.
  Waits for input channel to be closed."
  [ch]
  `(let [ch# ~ch] ;; we only need this when non-gensym symbols are used in macro bindings (unhygienic).
     ;; otherwise ch can and should never be shadowed, right?
     (<! (async/into [] ch#))))


(defmacro <<?
  "Takes multiple results from a channel and returns them as a vector.
  Throws if any result is an exception. Waits for input channel to be
  closed."
  [ch]
  `(->> (<<! ~ch)
        (map throw-if-throwable)
        ; doall to check for throwables right away
        (doall)))

;; TODO lazy-seq vs. vector in <<! ?
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
         results#))))

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
         results#))))

(defn <!!*
  [chs]
  (<!! (go (<!* chs))))

(defn <??*
  [chs]
  (<?? (go-try (<?* chs))))

;; transducer for parallelism?
(defn pmap>>
  "Takes objects from ch, asynchrously applies function f> (function
  should return channel), takes the result from the returned channel
  and if it's not nil, puts it in the results channel. Returns the
  results channel. Closes the returned channel when the input channel
  has been completely consumed and all objects have been processed. f>
  should not do io-heavy jobs as this can dry out the core.async
  thread pool for high degrees of parallelism, use async/thread to
  wrap such tasks."
  [f> parallelism ch]
  (let [results (async/chan)
        threads (atom parallelism)]
    (dotimes [_ parallelism]
      (go
        (loop []
          (when-let [obj (<! ch)]
            (if (instance? Throwable obj)
              (do
                (>! results obj)
                (async/close! results))
              (do
                (when-let [result (<! (f> obj))]
                  (>! results result))
                (recur)))))
        (when (zero? (swap! threads dec))
          (async/close! results))))
    results))

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


;; taken from clojure/core ~ 1.7
(defmacro ^{:private true} assert-args
  [& pairs]
  `(do (when-not ~(first pairs)
         (throw (IllegalArgumentException.
                 (str (first ~'&form) " requires " ~(second pairs) " in " ~'*ns* ":" (:line (meta ~'&form))))))
       ~(let [more (nnext pairs)]
          (when more
            (list* `assert-args more)))))


(defmacro go-for
  "List comprehension adapted from clojure.core 1.7. Takes a vector of
  one or more binding-form/collection-expr pairs, each followed by
  zero or more modifiers, and yields a channel of evaluations of
  expr. It is eager on all but the outer-most collection. TODO

  Collections are iterated in a nested fashion, rightmost fastest, and
  nested coll-exprs can refer to bindings created in prior
  binding-forms.  Supported modifiers are: :let [binding-form expr
  ...],
   :while test, :when test.

  (<! (async/into [] (go-for [x (range 10) :let [y (<! (go 4))] :while (< x y)] [x y])))"
  {:added "1.0"}
  [seq-exprs body-expr]
  (assert-args
   (vector? seq-exprs) "a vector for its binding"
   (even? (count seq-exprs)) "an even number of forms in binding vector")
  (let [to-groups (fn [seq-exprs]
                    (reduce (fn [groups [k v]]
                              (if (keyword? k)
                                (conj (pop groups) (conj (peek groups) [k v]))
                                (conj groups [k v])))
                            [] (partition 2 seq-exprs)))
        err (fn [& msg] (throw (IllegalArgumentException. ^String (apply str msg))))
        emit-bind (fn emit-bind [res-ch [[bind expr & mod-pairs]
                                        & [[_ next-expr] :as next-groups]]]
                    (let [giter (gensym "iter__")
                          gxs (gensym "s__")
                          do-mod (fn do-mod [[[k v :as pair] & etc]]
                                   (cond
                                     (= k :let) `(let ~v ~(do-mod etc))
                                     (= k :while) `(when ~v ~(do-mod etc))
                                     (= k :when) `(if ~v
                                                    ~(do-mod etc)
                                                    (recur (rest ~gxs)))
                                     (keyword? k) (err "Invalid 'for' keyword " k)
                                     next-groups
                                     `(let [iterys# ~(emit-bind res-ch next-groups)
                                            fs# (<? (iterys# ~next-expr))]
                                        (if fs#
                                          (concat fs# (<? (~giter (rest ~gxs))))
                                          (recur (rest ~gxs))))
                                     :else `(do (>! ~res-ch ~body-expr)
                                                (<? (~giter (rest ~gxs))))
                                     #_`(cons ~body-expr (<? (~giter (rest ~gxs))))))]
                      `(fn ~giter [~gxs]
                         (go-try
                          (loop [~gxs ~gxs]
                            (let [~gxs (seq ~gxs)]
                              (when-let [~bind (first ~gxs)]
                                ~(do-mod mod-pairs))))))))
        res-ch (gensym "res_ch__")]
    `(let [~res-ch (chan)
           iter# ~(emit-bind res-ch (to-groups seq-exprs))]
       (go (try (<? (iter# ~(second seq-exprs)))
                (catch Throwable e#
                  (>! ~res-ch e#))
                (finally (async/close! ~res-ch))))
       ~res-ch)))
