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
  (:require [clojure.string :refer [split capitalize, lower-case]])
  (:import (camelsnake Converter)))

(defn- map-keys
  [key-fn m]
  (letfn [(mapper [[k v]] [(key-fn k) (if (map? v) (map-keys key-fn v) v)])]
    (into {} (map mapper m))))

(defn ->camelCase [item]
  (Converter/convert (name item) nil false true))

(defn ->snake_case [item]
  (Converter/convert (name item) "_" false false))

(defn ->kebab-case [item]
  (Converter/convert (name item) "-" false false))

(defn ->kebab-case-keyword [item]
  (keyword (->kebab-case item)))

(defn ->camelCase-keys [m]
  (map-keys ->camelCase m))

(defn ->keyword-keys [m]
  (map-keys ->kebab-case-keyword m))

(defn ->keyword [kw]
  (when kw (-> kw (.replaceAll " " "-") ->kebab-case keyword)))
