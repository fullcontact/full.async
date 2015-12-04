(ns full.t-async
  (:require [midje.sweet :refer :all]
            [full.async :refer :all]
            [clojure.core.async :refer [<!! >! >!! go chan close!] :as async]))

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
    => #{2 3})

  (fact
    (let [ch1 (chan)
          ch2 (chan)]
      (go (doto ch2 (>!! 3) (>!! 4) close!))
      (go (doto ch1 (>!! 1) (>!! 2) close!))
      (<<?? (concat>> ch1 ch2))
      => [1 2 3 4]))

  (fact
    (->> (let [ch (chan)]
           (go (doto ch (>!! 1)
                        (>!! 2)
                        (>!! 3)
                        close!))
           ch)
         (partition-all>> 2)
         (<<??))
    => [[1 2] [3]])

  (fact
    (try<??
      (go-try (throw (Exception.)))
      false
      (catch Exception _
        true))
    => true))


(facts "full.async/count>"
  (fact (<!! (count> (async/to-chan [1 2 3 4]))) => 4)
  (fact (<!! (count> (async/to-chan []))) => 0))
