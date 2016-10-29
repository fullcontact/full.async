(ns full.async-test
  (:require
    [cemerick.cljs.test :as t :refer-macros [deftest is done]]
    [cljs.core.async :refer [chan take! <! >! close!]]
    [full.async :refer-macros [<? <<? <?* go-try go-retry]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(deftest
  ^:async test-<?
  (take! (go
           (let [ch (chan 1)]
             (>! ch "foo")
             (close! ch)
             (<? ch)))
         (fn [res]
           (is (= res "foo"))
           (done))))

(deftest
  ^:async test-<<?
  (take! (go
           (let [ch (chan 2)]
             (>! ch "1")
             (>! ch "2")
             (close! ch)
             (<<? ch)))
         (fn [res]
           (is (= res ["1" "2"]))
           (done))))

(deftest
  ^:async test-go-try-<?
  (take! (go
           (is (thrown? js/Error
                        (<? (go-try
                              (throw (js/Error.))))))
           (done))
         (fn [res])))


(deftest ^:async test-<?*
         (go
           (is (= (<?* [(go "1") (go "2")])
                  ["1" "2"]))
           (is (= (<?* (list (go "1") (go "2")))
                  ["1" "2"]))
           (is (thrown? js/Error
                        (<?* [(go "1") (go (js/Error))])))
           (done)))

(deftest
  ^:async test-pmap>>
  (go
    (is (= (->> (let [ch (chan)]
                  (go (doto ch (>! 1) (>! 2) close!))
                  ch)
                (full.async/pmap>> #(go (inc %)) 2)
                (<<?)
                (set))
           #{2 3}))
    (done)))

(deftest
  ^:async go-retry
  (go
    (is (= (<? (go-retry
                 {:retries 3}
                 "foo"))
           "foo"))
    (is (= (let [times- (atom 0)]
             (<? (go-retry
                   {:should-retry-fn (fn [res] (= 0 res))
                    :retries         3
                    :delay           0.1}
                   (swap! times- inc)
                   0))
             @times-)
           ; should be 4 (first attempt + 3 retries)
           4))
    (let [times- (atom 0)]
      (is (= (<? (go-retry
                   {:retries 5
                    :delay   0.1}
                   (if (> 3 (swap! times- inc))
                     (throw (js/Error. "Foo"))
                     "Bar")))
             "Bar"))
      (is (= @times- 3)))
    (done)))
