;;;;         _
;;;;     .--' |
;;;;    /___^ |     .--.
;;;;        ) |    /    \
;;;;       /  |  /`      '.
;;;;      |   '-'    /     \
;;;;      \         |      |\
;;;;       \    /   \      /\|
;;;;        \  /'----`\   /
;;;;        |||       \\ |
;;;;        ((|        ((|
;;;;        |||        |||
;;;;       //_(       //_(
;;;;
;;;;    camelsnake - transforming strings between cases


(ns camelsnake.core
  (:require [clojure.string :refer [split capitalize, lower-case]]))

(defn- map-keys
  [key-fn m]
  (letfn [(mapper [[k v]] [(key-fn k) (if (map? v) (map-keys key-fn v) v)])]
    (into {} (map mapper m))))

(defn- sliding [size f coll] (mapv f (partition size 1 coll)))

(defn- to-chars [item] (mapv str item))

;; Case matchers
(defn- item-contains? [item, s] (some #(= % s) (to-chars item)))
(defn- camel-case? [item] (re-find #"[A-Z]" item))
(defn- upper-case? [item] (some? (re-matches #"[A-Z]" (str item))))

(defn- case-check [item]
  (if (every? upper-case? item)
    (lower-case (second item))
    (second item)))

(defn- check-last [item]
  (if (upper-case? (nth item (- (count item) 2 )))
    (lower-case (last item))
    (last item)))


;; Lowercases items so that only first item of group is uppercase, which
;; helps to split things correctly. eg HTTPRequestParser -> HttpRequestParser
(defn- lower-case-items [items]
  (let [vitems (vec items)]
    (if (< 2 (count vitems))
      (conj (into [(first vitems)] (sliding 3 case-check vitems)) (check-last vitems))
      (map lower-case vitems))))

(defn- split-camel-case [raw-item]
  (let [item (vec (lower-case-items raw-item))]
    (reduce
      (fn [acc item] (if (upper-case? item) (conj acc (lower-case item)) (conj (vec (butlast acc)) (str (last acc) item))))
      []
      (to-chars item))))

(defn- item-split [raw-item]
  (let [item (name raw-item)]
    (cond
      (item-contains? item "-") (split item #"-")
      (item-contains? item "_") (split item #"_")
      (camel-case? item) (split-camel-case item)
      :else (vector item))))


(defn ->camelCase [item]
  (let [parts (item-split item)]
    (apply str (conj (map capitalize (rest parts)) (first parts)))))

(defn ->snake_case [item]
  (let [parts (item-split item)]
    (apply str (butlast (interleave parts (iterate str "_"))))))

(defn ->kebab-case [item]
  (let [parts (item-split item)]
    (apply str (butlast (interleave parts (iterate str "-"))))))

(defn ->kebab-case-keyword [item]
  (keyword (->kebab-case item)))

(defn ->camelCase-keys [m]
  (map-keys ->camelCase m))

(defn ->keyword-keys [m]
  (map-keys ->kebab-case-keyword m))

(defn ->keyword [kw]
  (when kw (-> kw (.replaceAll " " "-") ->kebab-case keyword)))

