(ns full.t-async
  (:refer-clojure :exclude [reduce into merge map])
  (:require [midje.sweet :refer :all]
            [full.async :refer :all]))

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
    (<??* [(go "1") (go (Exception. ))])
    => (throws Exception)))
