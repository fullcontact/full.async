(ns full.t-edn
  (:require [midje.sweet :refer :all]
            [full.edn :refer :all]
            [full.core.sugar :refer :all]
            [clj-time.core :as t]))

(facts
  "about EDN"
  (let [data [:keyword
              {1 2}
              #{"a" "b"}
              (t/date-time 2014 1 2 3 4 5 600)]
        string "[:keyword {1 2} #{\"a\" \"b\"} #inst \"2014-01-02T03:04:05.600-00:00\"]"]
    (fact (write-edn data) => string)

    (fact
      (read-edn string) => data)))
