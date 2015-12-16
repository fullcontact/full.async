(ns full.core.t-sugar
  (:require [midje.sweet :refer :all]
            [full.core.sugar :refer :all]))


(facts "about ?assoc"
  (?assoc {} :empty nil) => {})


(facts "about remove-prefix"
  (remove-prefix "aaabbb" "aaa") => "bbb"
  (remove-prefix "aaabbb" "ccc") => "aaabbb"
  (remove-prefix nil "ccc") => nil)


(facts "about remove-suffix"
  (remove-suffix "aaabbb" "bbb") => "aaa"
  (remove-suffix "aaabbb" "ccc") => "aaabbb"
  (remove-suffix nil "ccc") => nil)


(facts "about ascii"
  (ascii "ĀČĒāčēAce") => "ACEaceAce")


(facts "about insert-at"
  (insert-at [] 0 "x") => ["x"]
  (insert-at '() 0 "x") => '("x")
  (insert-at [] 5 "x") => ["x"]
  (insert-at [1 2 3 4] 0 0) => [0 1 2 3 4]
  (insert-at [1 2 3 4] 1 0) => [1 0 2 3 4]
  (insert-at [1 2 3 4] 3 0) => [1 2 3 0 4]
  (insert-at [1 2 3 4] 5 0) => [1 2 3 4 0])


(facts "about remove-at"
  (remove-at [] 0) => []
  (remove-at '() 5) => '()
  (remove-at '(1 1 2 3) 1) => '(1 2 3)
  (remove-at ["a"] 0) => []
  (remove-at [1 2 3 4] 0) => [2 3 4]
  (remove-at [1 2 3 4] 1) => [1 3 4]
  (remove-at [1 2 3 4] 3) => [1 2 3]
  (remove-at [1 2 3 4] 5) => [1 2 3 4])


(facts "about ?conj"
  (?conj [] 1) => [1]
  (?conj [] nil) => []
  (?conj [1] 2 3) => [1 2 3]
  (?conj [1] nil 3) => [1 3])


(facts "about conditional threading"
  (->> (range 10)
       (map inc)
       (when->> true (filter even?))) => '(2 4 6 8 10)

  (->> (range 10)
       (map inc)
       (when->> false (filter even?))) => '(1 2 3 4 5 6 7 8 9 10)

  (-> "foobar"
      (clojure.string/upper-case)
      (when-> true (str "baz"))) => "FOOBARbaz"

  (-> "foobar"
      (clojure.string/upper-case)
      (when-> false (str "baz"))) => "FOOBAR")


(facts "about ?hash-map"
  (?hash-map :foo nil :bar nil :baz "xx") => {:baz "xx"}
  (?hash-map :foo nil ) => {})


(facts "about update-last"
  (update-last [] inc) => []
  (update-last [1] inc) => [2]
  (update-last [1 2 3] inc) => [1 2 4])


(facts "about update-first"
  (update-first [] inc) => []
  (update-first [1] inc) => [2]
  (update-first [1 2 3] inc) => [2 2 3])


(facts "about number formatting"
  (num->compact 0.1) => "0.1"
  (num->compact 0.11) => "0.11"
  (num->compact 0.19) => "0.19"
  (num->compact 0.191) => "0.19"
  (num->compact 0.199) => "0.2"
  (num->compact 1) => "1"
  (num->compact 1.12) => "1.12"
  (num->compact 10.12) => "10.1"
  (num->compact 100.12) => "100"
  (num->compact 1000) => "1K"
  (num->compact 1290) => "1.29K"
  (num->compact 1029) => "1.03K"
  (num->compact 10290) => "10.3K"
  (num->compact 102900) => "103K"
  (num->compact 950050) => "950K"
  (num->compact 1000000) => "1M"
  (num->compact 1200000) => "1.2M"
  (num->compact 1251000) => "1.25M"
  (num->compact 11251000) => "11.3M"
  (num->compact 911251000) => "911M"
  (num->compact 1911251000) => "1.91B"
  (num->compact 11911251000) => "11.9B"
  (num->compact 119112510000) => "119B"
  (num->compact 1191125100000) => "1.19T")


(facts "about ?update and ?update-in"
  (?update-in {} [:foo] inc) => {}
  (?update-in {:foo 0} [:foo] inc) => {:foo 1}
  (?update-in {:foo 0} [:foo] (constantly nil)) => {}
  (?update-in {:foo {:bar  0}} [:foo :bar] inc) => {:foo {:bar 1}}
  (?update-in {:foo {:bar 0}} [:foo :bar] (constantly nil)) => {:foo {}}

  (?update {} :foo inc) => {}
  (?update {:foo 0} :foo inc) => {:foo 1}
  (?update {:foo 0} :foo (constantly nil)) => {})


(facts "about juxt-partition"
  (juxt-partition odd? [1 2 3 4] filter remove) => ['(1 3) '(2 4)]
  (juxt-partition odd? [1 2 3 4] remove filter) => ['(2 4) '(1 3)])


(facts "about transients"
  (facts "first!"
    (first! (transient [4 2 8])) => 4
    (first! (transient [])) => nil)

  (facts "last!"
    (last! (transient [5 6 7])) => 7
    (last! (transient [])) => nil)

  (facts "update-last!"
    (persistent! (update-last! (transient [1 4 10]) inc)) => [1 4 11])

  (facts "update-first!"
    (persistent! (update-first! (transient [1 4 10]) inc)) => [2 4 10]))
