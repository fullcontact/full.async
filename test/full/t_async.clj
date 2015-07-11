(ns full.t-async
  (:require [midje.sweet :refer :all]
            [full.async :refer :all]
            [clojure.core.async :refer [<!! <! >! >!! go chan close! onto-chan timeout]]))

(facts
 (fact
  (<!! (go (let [ch (chan 2)]
             (>! ch "1")
             (>! ch "2")
             (close! ch)
             (<<! ch))))
  => ["1" "2"])

 (fact
  (<!! (go (let [ch (chan 2)]
             (>! ch "1")
             (>! ch "2")
             (close! ch)
             (<<! ch))))
  => ["1" "2"])

 (fact
  (<?? (go-try (let [ch (chan 2)]
                 (>! ch "1")
                 (>! ch (Exception.))
                 (close! ch)
                 (<<? ch))))
  => (throws Exception))

 (fact
  (<<!! (let [ch (chan 2)]
          (>!! ch "1")
          (>!! ch "2")
          (close! ch)
          ch))
  => ["1" "2"])

 (fact
  (<!! (go (<<! (let [ch (chan 2)]
                  (>! ch "1")
                  (>! ch "2")
                  (close! ch)
                  ch))))
  => ["1" "2"])

 (fact
  (<<?? (let [ch (chan 2)]
          (>!! ch "1")
          (>!! ch (Exception.))
          (close! ch)
          ch))
  => (throws Exception))

 (fact
  (<!!* [(go "1") (go "2")])
  => ["1" "2"])

 (fact
  (<??* [(go "1") (go "2")])
  => ["1" "2"])

 (fact
  (<??* (list (go "1") (go "2")))
  => ["1" "2"])

 (fact
  (<??* [(go "1") (go (Exception. ))])
  => (throws Exception))

 (fact
  (->> (let [ch (chan)]
         (go (doto ch (>!! 1) (>!! 2) close!))
         ch)
       (pmap>> #(go (inc %)) 2)
       (<<??)
       (set))
  => #{2 3}))

(fact
 (<?? (go-try (alt? (timeout 100) 43
                    :default (ex-info "foo" {}))))
 => (throws Exception))


;; go-loop-try
(fact
 (<<?? (go-loop-try [[f & r] [1 0]]
                    (/ 1 f)
                    (recur r)))
 => (throws Exception))

;; go-try>
(fact
 (let [err-ch (chan)]
   (go-try> err-ch
            (/ 1 0))
   (<?? err-ch)
   => (throws Exception)))

;; go-loop-try>
(fact
 (let [err-ch (chan)]
   (<<?? (go-loop-try> err-ch
                       [[f & r] [1 0]]
                       (/ 1 f)
                       (recur r)))
   (<?? err-ch)
   => (throws Exception)))

;; go-for
(fact ;; traditional for comprehension
 (<<?? (go-for [a (range 5)
                :let [c 42]
                b [5 6]
                :when (even? b)]
               [a b c]))
 => '([0 6 42] [1 6 42] [2 6 42] [3 6 42] [4 6 42]))


(fact ;; async operations spliced in bindings and body
 (<<?? (go-for [a [1 2 3]
                :let [b (<! (go (* a 2)))]]
               (<! (go [a b]))))
 => '([1 2] [2 4] [3 6]))


(facts ;; async operations propagate exceptions
 (<<?? (go-for [a [1 2 3]
                :let [b 0]]
               (/ a b)))
 => (throws Exception)

 (<<?? (go-for [a [1 2 3]
                :let [b (/ 1 0)]]
               42))
 => (throws Exception))
