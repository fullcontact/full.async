(ns full.json
  (:require [cheshire.core :as json]
            [cheshire.generate :as json-gen]
            [camelsnake.core :refer :all]
            [full.core.sugar :refer :all]
            [full.time :refer :all]
            [clojure.walk :refer [postwalk]])
  (:import (org.joda.time DateTime LocalDate)))


(json-gen/add-encoder DateTime (fn [c jsonGenerator] (.writeString jsonGenerator (dt->iso-ts c))))
(json-gen/add-encoder LocalDate (fn [c jsonGenerator] (.writeString jsonGenerator (d->iso-d c))))
(json-gen/add-encoder Character (fn [c jsonGenerator] (.writeString jsonGenerator (str c))))

(defn get-preserved-keys
  ":preserve-keys takes a vector of paths (as in multiple get-in).
  Child keys of those won't be kebabized or transformed
  to keywords. so it will retain whatever casing is in them."
  [json-map preserve-keys]
  (into {} (for [pres-key preserve-keys
                 :let [pres-val (get json-map pres-key)]
                 :when pres-val]
             [(->kebab-case-keyword pres-key) pres-val])))

(defn- kebabize-single [json-map preserve-keys]
  (if (map? json-map)
    (let [preserved-map (get-preserved-keys json-map preserve-keys)
          walk-fn (fn [[k v]] [(->kebab-case-keyword k) v])
          kebabized-json (postwalk (fn [x] (if (map? x) (into {} (map walk-fn x)) x)) json-map)]
      (merge kebabized-json preserved-map))
    json-map))

(defn kebabize-keys [json-map preserve-keys]
  (if (map? json-map)
    (kebabize-single json-map preserve-keys)
    (map #(kebabize-single % preserve-keys) json-map)))

(defn read-json
  "Parses a JSON string and returns a hash-map"
  [raw & {:keys [preserve-keys] :or {preserve-keys []}}]
  (when raw
    (-> (json/parse-string raw)
        (kebabize-keys preserve-keys))))

(defn write-json [obj & {:keys [json-key-fn]
                         :or {json-key-fn ->camelCase}}]
  (json/generate-string obj {:key-fn json-key-fn}))

(defn slurp-json [path]
  (read-json (slurp path)))
