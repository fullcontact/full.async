(ns full.edn
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [full.core.sugar :refer :all]
            [full.time :refer :all]))


(def ^:private edn-reader-opts
  {:readers (assoc *data-readers* 'inst read-instant-dt)})

(defn read-edn
  "Deserialize EDN string. Main difference from vanilla read-string is that
   #inst timestamps are read as Joda DateTime not java.util.Date."
  [string]
  (edn/read-string edn-reader-opts string))

(defn read-edn-resource
  "Load and deserialize and EDN resource file. #inst timestamps are read as
  Joda DateTime instances. Returns nil if resource does not exist."
  [resource-path]
  (some->> (io/resource resource-path)
           (io/reader)
           (java.io.PushbackReader.)
           (edn/read edn-reader-opts)))
