(ns full.core.sugar
  (:import (java.text Normalizer Normalizer$Form)
           (org.apache.commons.codec.binary Hex)
           (java.net URLEncoder)
           (java.util.concurrent LinkedBlockingQueue))
  (:require [clojure.walk :refer [postwalk]]
            [clojure.string :as str]))


;;; MAP HELPERS


(defn ?assoc
  "Same as assoc, but skip the assoc if v is nil http://stackoverflow.com/a/16357241"
  [m & kvs]
  (->> (partition 2 kvs)
       (filter (comp not nil? second))
       (map vec)
       (into m)))

(defn assoc-first
  "Replaces value of key `k` in map `m` with the first value  sequence  first item from map given key to resultant map."
  [m k]
  (if-let [v (get m k)]
    (assoc m k (first v))
    m))

(defn transf
  "Transforms the value of key `k` in map `m` by applying function `f`."
  [m f k]
  (assoc m k (f (get m k))))

(defn ?transf
  "Same as transform, but will remove the `k` from `m` if the transformed value is nil or false."
  [m f k]
  (?assoc m k (f (get m k))))

(defn remove-empty-val
  "Filter empty? values from map."
  [m]
  (into {} (filter (fn [[key val]] (and (not (nil? val))
                                   (or (and (not (coll? val)) (not (string? val)))
                                       (seq val)))) m)))

(defn remove-nil-val
  "Filter nil? values from map."
  [m]
  (into {} (remove (comp nil? val) m)))

(defn dissoc-in
  [m [k & ks]]
  (if ks
    (if-let [submap (get m k)]
      (assoc m k (dissoc-in submap ks))
      m)
    (dissoc m k)))

(defn move-in [m from to]
  (-> (assoc-in m to (get-in m from))
      (dissoc-in from)))

(defn ?move-in [m from to]
  (if (get-in m from)
    (move-in m from to)
    m))

(defn ?update-in [m k f]
  (if (get-in m k)
    (update-in m k f)
    m))

(defn move-map-in [m f from to]
  (-> (assoc-in m to (f (get-in m from)))
      (dissoc-in from)))

(defn copy-in [m from to]
  (assoc-in m to (get-in m from)))

(defn map-map
  ([key-fn m] (map-map key-fn (fn [v] v) m))
  ([key-fn value-fn m]
   (letfn [(mapper [[k v]] [(key-fn k) (if (map? v) (map-map key-fn value-fn v) (value-fn v))])]
     (into {} (map mapper m)))))

(defn remap
  "Remap keys of `m` based on `mapping`."
  [m mapping]
  (into {} (map (fn [[key new-key]] [new-key (get m key)]) mapping)))

(defn map-value
  ([value-fn m]
   (into {} (for [[k v] m] [k (value-fn v)]))))

(defn mapply [f & args]
  (apply f (apply concat (butlast args) (last args))))

(defn ?hash-map [& keyvals]
  (apply ?assoc {} keyvals))


;;; LIST HELPERS


(defn insert-at
  "Returns the sequence s with the item i inserted at 0-based index idx."
  [s idx i]
  (apply conj (into (empty s) (take idx s)) (cons i ( nthrest s idx))))


(defn remove-at
  "Returns the sequence s with the element at 0-based index idx removed."
  [s idx]
  (let [vec-s (vec s)]
    (into (vec (take idx vec-s)) (nthrest vec-s (inc idx)))))

(defn replace-at
  "Returns the sequence s with the item at 0-based index idx."
  [s idx i]
  (apply conj (into (empty s) (take idx s)) (cons i ( nthrest s (inc idx)))))

(defn ?conj
  "Same as conj, but skip the conj if v is nil"
  [coll & xs]
  (let [filtered (filter identity xs)]
    (if (empty? filtered)
      coll
      (apply (partial conj coll) filtered))))

(defn update-last [s m]
  "Updates last item in sequence s by applying mapping method m to it."
  (if (seq s)
    (assoc s (dec (count s)) (m (last s)))
    s))

(defn update-first [s m]
  "Updates first item in sequence s by applying mapping method m to it."
  (if (seq s)
    (assoc s 0 (m (first s)))
    s))



;;; SEQ HELPERS


(defn pipe
  "Returns a vector containing a sequence that will read from the
   queue, and a function that inserts items into the queue.

   Source: http://clj-me.cgrand.net/2010/04/02/pipe-dreams-are-not-necessarily-made-of-promises/"
  []
  (let [q (LinkedBlockingQueue.)
        EOQ (Object.)
        NIL (Object.)
        s (fn queue-seq []
            (lazy-seq (let [x (.take q)]
                        (when-not (= EOQ x)
                          (cons (when-not (= NIL x) x)
                                (queue-seq))))))]
    [(s) (fn queue-put
           ([] (.put q EOQ))
           ([x] (.put q (or x NIL))))]))

(def all? (partial every? identity))

(defn filter-indexed [pred coll]
  (filter pred (map-indexed vector coll)))

(defn some-when
  "Similiar to some but returns matching value instead of predicates result."
  [pred coll]
  (some #(when (pred %) %) coll))

(defn idx-of
  "Similar to .indexOf, but works with lazy collections as well."
  [collection item]
  (or (first (some-when (fn [{v 1}] (= v item)) (map-indexed vector collection))) -1))


;;; STRING HELPERS


(defn number-or-string [s]
  (try
    (Integer/parseInt s)
    (catch Exception _ s)))

(defn remove-prefix [s prefix]
  (if (and s (.startsWith s prefix))
    (.substring s (.length prefix))
    s))

(defn replace-prefix [s prefix new-prefix]
  (if (and s (.startsWith s prefix))
    (str new-prefix (.substring s (.length prefix)))
    s))

(defn remove-suffix [s suffix]
  (if (and s (.endsWith s suffix))
    (.substring s 0 (- (.length s) (.length suffix)))
    s))

(defn ascii
  "Ensures all characters in the given string are converted to ASCII (such as ā->a)."
  [s]
  (.replaceAll (Normalizer/normalize s Normalizer$Form/NFD)
               "\\p{InCombiningDiacriticalMarks}+"
               ""))

(defn dq
  "Converts single quotes to double quotes."
  [s]
  (.replaceAll s "'" "\""))

(defn query-string [m]
  (clojure.string/join "&" (for [[k v] m] (str (name k) "="  (URLEncoder/encode (str v))))))

(defn strip [coll chars]
  (apply str (remove #((set chars) %) coll)))

(defn uuid [] (str/replace (str (java.util.UUID/randomUUID)) "-" ""))

(defn byte-buffer->byte-vector [bb]
  (loop [byte-vector []]
    (if (= (.position bb) (.limit bb))
      byte-vector
      (recur (conj byte-vector (.get bb))))))

(defn byte-buffer->hex-string [byte-buffer]
  (->> (byte-buffer->byte-vector byte-buffer)
       (into-array Byte/TYPE)
       (Hex/encodeHex)
       (clojure.string/join)))


;;; METADATA HELPERS


(defn def-name
  "Returns human readable name of defined symbol (such as def or defn)."
  [sym]
  (-> `(name ~sym)
      (second)
      (str)
      (str/split #"\$")
      (last)
      (str/split #"@")
      (first)
      (str/replace #"__" "-")
      (str/replace #"GT_" ">")))

(defn mname [obj]
  (-> obj meta :name))

(defmacro with-mname [name & body]
  `(with-meta ~@body {:name ~name}))

(defmacro fn-name [name args & body]
  `(with-mname ~name (fn ~args ~@body)))


;;; CONDITIONAL THREADING


(defn- cndexpand [cnd value]
  (postwalk (fn [c] (if (= (symbol "%") c) value c)) cnd))

;; Using when + ->> inside ->> threads
;; (->> (range 10) (map inc) (when->> true (filter even?))) => (2 4 6 8 10)

(defmacro when->> [cnd & threads]
  `(if ~(cndexpand cnd (last threads))
    (->> ~(last threads)
         ~@(butlast threads))
    ~(last threads)))

;; Using when + -> inside -> threads
;; (-> "foobar" (upper-case) (when-> true (str "baz"))) => "FOOBARbaz"
(defmacro when-> [thread cnd & threads]
  `(if ~(cndexpand cnd thread)
    (-> ~thread
        ~@threads)
    ~thread))

;; Using when + -> inside ->> threads
;; (->> (range 3) (map inc) (when->>-> true (into ["header"]))) => ("header" 1 2 3)
(defmacro when->>-> [cnd & threads]
  `(if ~(cndexpand cnd (last threads))
    (-> ~(last threads)
        ~@(butlast threads))
    ~(last threads)))

;; Using if + ->> inside ->> threads
;; "else" block can be provided after an :else keyword.
;; All parts work like you'd expect inside ->> and when->>.
;; (->> (range 10) (if->> false (filter odd?) (map inc) :else (filter even?) (map dec))) => (-1 1 3 5 7)
(defmacro if->> [cnd & threads]
  `(if ~(cndexpand cnd (last threads))
    (->> ~(last threads)
         ~@(take-while #(not= :else %) (butlast threads)))
    (->> ~(last threads)
         ~@(rest (drop-while #(not= :else %) (butlast threads))))))


;; Allowing to sneak in ->s inside ->>s.
;; (->> (range 3) (map inc) (as-> (nth 1) inc) (str "x")) => (str "x3")

(defmacro nest-> [& threads]
  `(-> ~(last threads)
       ~@(butlast threads)))


;;; HELPERS FOR MEASURING EXECUTION TIME


(defn time-bookmark
  "Returns time bookmark (technically system time in nanoseconds) for use in concert with ellapsed-time to
  messure execution time of some code block."
  []
  (. System (nanoTime)))

(defn ellapsed-time
  "Returns ellapsed time in milliseconds since the time bookmark."
  [time-bookmark]
  (/ (double (- (. System (nanoTime)) time-bookmark)) 1000000.0))
