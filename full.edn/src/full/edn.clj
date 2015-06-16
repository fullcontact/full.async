(ns full.edn
  (:require [clojure.edn :as edn]
            [full.core.sugar :refer :all]
            [full.time :refer :all]))

 (defn read-edn
   "Deserialize EDN string. Main difference from vanilla read-string is that
   #inst timestamps are read as Joda DateTime not java.util.Date."
   [string]
   (let [opts {:readers (assoc *data-readers* 'inst read-instant-dt)}]
     (edn/read-string opts string)))

(defn write-edn
  [object]
  (pr-str object))