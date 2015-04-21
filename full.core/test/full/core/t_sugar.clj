(ns full.core.t-sugar
  (:require [midje.sweet :refer :all]
            [full.core.sugar :refer :all]))

(facts
  "about ?assoc"
  (fact
    (?assoc {} :empty nil) => {}))

(facts "fox.sugar/remove-prefix"
       (remove-prefix "aaabbb" "aaa") => "bbb"
       (remove-prefix "aaabbb" "ccc") => "aaabbb"
       (remove-prefix nil "ccc") => nil)

(facts "fox.sugar/remove-suffix"
       (remove-suffix "aaabbb" "bbb") => "aaa"
       (remove-suffix "aaabbb" "ccc") => "aaabbb"
       (remove-suffix nil "ccc") => nil)

(facts " fox.sugar/ascii"
       (ascii "ĀČĒāčēAce") => "ACEaceAce")

(facts "about insert-at form"
       (insert-at [] 0 "x") => ["x"]
       (insert-at '() 0 "x") => '("x")
       (insert-at [] 5 "x") => ["x"]
       (insert-at [1 2 3 4] 0 0) => [0 1 2 3 4]
       (insert-at [1 2 3 4] 1 0) => [1 0 2 3 4]
       (insert-at [1 2 3 4] 3 0) => [1 2 3 0 4]
       (insert-at [1 2 3 4] 5 0) => [1 2 3 4 0])

(facts "about remove-at form"
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
