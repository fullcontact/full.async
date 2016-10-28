(ns full.async-test
  (:require [clojure.test :refer :all]
            [full.async :as full.async :refer [<? <?? <<? <<! <<!! <<?? <!!*
                                               <??* go-try go-retry]]
            [clojure.core.async :refer [<!! >! >!! go] :as async]))

(deftest test-<<!
  (is (= (<!! (go (let [ch (async/chan 2)]
                    (>! ch "1")
                    (>! ch "2")
                    (async/close! ch)
                    (<<! ch))))
         ["1" "2"]))
  (is (= (<!! (go (<<! (let [ch (async/chan 2)]
                         (>! ch "1")
                         (>! ch "2")
                         (async/close! ch)
                         ch))))
         ["1" "2"])))

(deftest test-<<?
  (is (thrown? Exception
               (<?? (go-try
                      (let [ch (async/chan 2)]
                        (>! ch "1")
                        (>! ch (Exception.))
                        (async/close! ch)
                        (<<? ch)))))))

(deftest test-<<!!
  (is (= (<<!! (let [ch (async/chan 2)]
                 (>!! ch "1")
                 (>!! ch "2")
                 (async/close! ch)
                 ch))
         ["1" "2"])))

; TODO this breaks? does seem to work on midje
(deftest test-<<??
  (is (thrown? Exception
               (doall
                 (<<?? (let [ch (async/chan 2)]
                         (>!! ch "1")
                         (>!! ch (Exception.))
                         (async/close! ch)
                         ch))))))

(deftest test-<!!*
  (is (= (<!!* [(go "1") (go "2")])
         ["1" "2"])))

(deftest test-<??*
  (is (= (<??* [(go "1") (go "2")])
         ["1" "2"]))
  (is (= (<??* (list (go "1") (go "2")))
         ["1" "2"]))
  (is (thrown? Exception
               (<??* [(go "1") (go (Exception. ))]))))

(deftest test-pmap>>
  (is (= (->> (let [ch (async/chan)]
                (go (doto ch (>!! 1) (>!! 2) async/close!))
                ch)
              (full.async/pmap>> #(go (inc %)) 2)
              (<<??)
              (set))
         #{2 3})))

(deftest test-concat>>
  (is (= (let [ch1 (async/chan)
               ch2 (async/chan)]
           (go (doto ch2 (>!! 3) (>!! 4) async/close!))
           (go (doto ch1 (>!! 1) (>!! 2) async/close!))
           (<<?? (full.async/concat>> ch1 ch2)))
         [1 2 3 4])))

(deftest test-partition-all>>
  (is (= (->> (let [ch (async/chan)]
                (go (doto ch (>!! 1)
                             (>!! 2)
                             (>!! 3)
                             async/close!))
                ch)
              (full.async/partition-all>> 2)
              (<<??))
         [[1 2] [3]])))

(deftest test-try<??
  (is (= (full.async/try<??
           (go-try (throw (Exception.)))
           false
           (catch Exception _
             true))
         true)))

(deftest test-count>
  (is (= (<!! (full.async/count> (async/to-chan [1 2 3 4]))) 4))
  (is (= (<!! (full.async/count> (async/to-chan []))) 0)))

(deftest test-go-retry
  (is (thrown? Exception
               (<?? (go-retry
                      {:retries 1
                       :delay 0.1}
                      ((throw (Exception. "Foo")))))))
  (let [times- (atom 0)
        error-side-effect (fn [_] (swap! times- inc))]
    (is (thrown? Exception
                 (<?? (go-retry
                        {:retries 5
                         :delay 0.1
                         :on-error error-side-effect}
                        (throw (Exception. "Foo"))))))
    (is (= 5 @times-)))
  (let [times- (atom 0)]
    (is (= (<?? (go-retry
                  {:retries 5
                   :delay   0.1}
                  (if (> 3 (swap! times- inc))
                    (throw (Exception. "Foo"))
                    "Bar")))
           "Bar"))
    (is (= 3 @times-))))
