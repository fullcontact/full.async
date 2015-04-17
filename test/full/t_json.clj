(ns full.t-json
  (:require [midje.sweet :refer :all]
            [full.json :refer :all]
            [full.core.sugar :refer :all]
            [camelsnake.core :refer :all]
            [clj-time.core :as t]))


(facts
  "about json parsing"
  (read-json (dq "{'testStr':'Abc'}")) => {:test-str "Abc"}
  (read-json (dq "['1', '2', '3', '4']")) => ["1" "2" "3" "4"]
  (read-json (dq "[{'a':'b'}]")) => [{:a "b"}]
  (write-json {:test-str "Abc"}) => (dq "{'testStr':'Abc'}")
  (write-json {:test-date (t/date-time 2014 1 2 3 4 5 678)}) => (dq "{'testDate':'2014-01-02T03:04:05.678Z'}")
  (write-json {:test-date (t/date-time 2014 1 2 3 4 5 678)} :json-key-fn nil) => (dq "{'test-date':'2014-01-02T03:04:05.678Z'}")
  (write-json {:test-date (t/date-time 2014 1 2 3 4 5 678)} :json-key-fn ->snake_case) => (dq "{'test_date':'2014-01-02T03:04:05.678Z'}"))


(facts
  "about preserving keys in json payloads"
  (fact
    (convert-keys {"foo" {"bar" 5}
                   "NaNoWriMo" {"fooBar" 3}
                   "XHTML" 3
                   "fooBarBaz" 5}
                  ->kebab-case-keyword
                  ["NaNoWriMo" "XHTML"])
    => {:foo {:bar 5}
        :na-no-wri-mo {"fooBar" 3}
        :xhtml 3
        :foo-bar-baz 5}))


(facts
  "about preserving header casing"
  (fact
    (read-json (dq "{'headers':{'Sav27MAVhP': {'If-Match':'deadbeef'}}, 'data':{'notes':'I am a note'}}") :preserve-keys ["headers"])
    => {:headers {"Sav27MAVhP" {"If-Match" "deadbeef"}}
        :data {:notes "I am a note"}})
  (fact
    (read-json (dq "[{'headers':{'Sav27MAVhP': {'If-Match':'deadbeef'}}, 'data':{'notes':'I am a note'}}]") :preserve-keys ["headers"])
    => [{:headers {"Sav27MAVhP" {"If-Match" "deadbeef"}}
         :data {:notes "I am a note"}}]))

