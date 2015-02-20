(ns full.t-async
  (:require [midje.sweet :refer :all]
            [full.async :refer :all]
            [clojure.core.async :refer [<!! >! >!! go chan close!]]))

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
