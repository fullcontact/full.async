(ns full.async-test
  (:require
    #?(:clj [clojure.test :refer :all]
       :cljs [cljs.test :refer-macros [deftest is async]])
    #?(:clj [clojure.core.async :refer [<!! <! >! >!! go] :as async]
       :cljs [cljs.core.async :refer [<! >!] :as async])
    #?(:clj [full.async :refer :all]
       :cljs [full.async :refer-macros [<<! <<? <? <?* go-try go-retry]]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]])))

(defn test-async
  "Asynchronous test awaiting ch to produce a value or close."
  [ch]
  #?(:clj (<!! ch)
     :cljs (async done (async/take! ch (fn [_] (done))))))

(defn e []
  #?(:clj (Exception.)
     :cljs (js/Error.)))

(deftest test-<?
  (test-async
    (go
      (= (<? (go
               (let [ch (async/chan 1)]
                 (>! ch "foo")
                 (async/close! ch)
                 (<? ch))))
         "foo"))))

(deftest test-go-try-<?
  (test-async
    (go
      (is (thrown? #?(:clj Exception :cljs js/Error)
                   (<? (go-try
                         (throw (e)))))))))

(deftest test-<<!
  (test-async
    (go
      (is (= (<! (go (let [ch (async/chan 2)]
                       (>! ch "1")
                       (>! ch "2")
                       (async/close! ch)
                       (<<! ch))))
             ["1" "2"]))
      (is (= (<! (go (<<! (let [ch (async/chan 2)]
                            (>! ch "1")
                            (>! ch "2")
                            (async/close! ch)
                            ch))))
             ["1" "2"])))))

(deftest test-<<?
  (test-async
    (go
      (is (thrown? #?(:clj Exception :cljs js/Error)
                   (<? (go-try
                         (let [ch (async/chan 2)]
                           (>! ch "1")
                           (>! ch (e))
                           (async/close! ch)
                           (<<? ch)))))))))

(deftest ^:async test-<?*
  (test-async
    (go
      (is (= (<?* [(go "1") (go "2")])
             ["1" "2"]))
      (is (= (<?* (list (go "1") (go "2")))
             ["1" "2"]))
      (is (thrown? #?(:clj Exception :cljs js/Error)
                   (<?* [(go "1") (go (e))]))))))

(deftest test-go-retry
  (test-async
    (go
      (is (= (<? (go-retry
                   {:retries 3}
                   "foo"))
             "foo"))
      (is (thrown? #?(:clj Exception :cljs js/Error)
                   (<? (go-retry
                         {:retries 1
                          :delay 0.1}
                         ((throw (e)))))))
      (let [times- (atom 0)
            error-side-effect (fn [_] (swap! times- inc))]
        (is (thrown? #?(:clj Exception :cljs js/Error)
                     (<? (go-retry
                           {:retries 5
                            :delay 0.1
                            :on-error error-side-effect}
                           (throw (e))))))
        (is (= 5 @times-)))
      (let [times- (atom 0)]
        (is (= (<? (go-retry
                     {:retries 5
                      :delay   0.1}
                     (if (> 3 (swap! times- inc))
                       (throw (e))
                       "Bar")))
               "Bar"))
        (is (= 3 @times-)))
      (is (= (let [times- (atom 0)]
               (<? (go-retry
                     {:should-retry-fn (fn [res] (= 0 res))
                      :retries         3
                      :delay           0.1}
                     (swap! times- inc)
                     0))
               @times-)
             ; should be 4 (first attempt + 3 retries)
             4)))))

(deftest test-pmap>>
  (test-async
    (go
      (is (= (->> (let [ch (async/chan)]
                    (go (doto ch (>! 1) (>! 2) async/close!))
                    ch)
                  (full.async/pmap>> #(go (inc %)) 2)
                  (<<?)
                  (set))
             #{2 3})))))

(deftest test-concat>>
  (test-async
    (go
      (is (= (let [ch1 (async/chan)
                   ch2 (async/chan)]
               (go (doto ch2 (>! 3) (>! 4) async/close!))
               (go (doto ch1 (>! 1) (>! 2) async/close!))
               (<<? (full.async/concat>> ch1 ch2)))
             [1 2 3 4])))))

(deftest test-partition-all>>
  (test-async
    (go
      (is (= (->> (let [ch (async/chan)]
                    (go (doto ch (>! 1)
                                 (>! 2)
                                 (>! 3)
                                 async/close!))
                    ch)
                  (full.async/partition-all>> 2)
                  (<<?))
             [[1 2] [3]])))))

(deftest test-count>
  (test-async
    (go
      (is (= (<! (full.async/count> (async/to-chan [1 2 3 4]))) 4))
      (is (= (<! (full.async/count> (async/to-chan []))) 0)))))


;;; CLOJURE ONLY


#?(:clj
   (do
     (deftest test-<<!!
       (is (= (<<!! (let [ch (async/chan 2)]
                      (>!! ch "1")
                      (>!! ch "2")
                      (async/close! ch)
                      ch))
              ["1" "2"])))

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

     (deftest test-try<??
       (is (= (full.async/try<??
                (go-try (throw (Exception.)))
                false
                (catch Exception _
                  true))
              true)))
     ))
