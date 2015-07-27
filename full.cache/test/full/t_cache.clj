(ns full.t-cache
  (:require [midje.sweet :refer :all]
            [full.core.sugar :refer :all]
            [full.async :refer :all]
            [full.cache :as cache]
            [clojure.core.async :as async :refer [<!!]]))

(facts
  "about remote cache"

  (fact
    "about concurrent gets with loading"
    (let [cache (atom {})
          set-cnt (atom {})
          get-cnt (atom {})
          load-cnt (atom 0)
          loader  #(go-try
                     (swap! load-cnt inc)
                     (<? (async/timeout 100))
                     "v1")]
      (with-redefs [cache/rset (fn [k v & _]
                                 (swap! cache assoc k v)
                                 (swap! set-cnt (fn [set-cnt] (update-in set-cnt [k] #(inc (or % 0)))))
                                 v)
                    cache/rget (fn [k & _]
                                 (swap! get-cnt (fn [get-cnt] (update-in get-cnt [k] #(inc (or % 0)))))
                                 (get @cache k))]
        (-> [(cache/rget-or-load> "k1" loader)
             (cache/rget-or-load> "k1" loader)
             (do (<?? (async/timeout 50)) (cache/rget-or-load> "k1" loader))]
            <??*)
        => (every-checker
             (fn [res] (= res ["v1" "v1" "v1"]))
             (fn [_] (= 3 (@get-cnt "k1")))
             (fn [_] (= 1 (@set-cnt "k1")))
             (fn [_] (= 1 @load-cnt))))))

  (fact
    "about single get with loading"
    (let [cache (atom {})
          set-cnt (atom {})
          get-cnt (atom {})
          load-cnt (atom 0)
          loader  #(go-try
                     (swap! load-cnt inc)
                     (<? (async/timeout 100))
                     "v1")]
      (with-redefs [cache/rset (fn [k v & _]
                                 (swap! cache assoc k v)
                                 (swap! set-cnt (fn [set-cnt] (update-in set-cnt [k] #(inc (or % 0)))))
                                 v)
                    cache/rget (fn [k & _]
                                 (swap! get-cnt (fn [get-cnt] (update-in get-cnt [k] #(inc (or % 0)))))
                                 (get @cache k))]
        (<?? (cache/rget-or-load> "k1" loader))
        => (every-checker
             (fn [res] (= res "v1"))
             (fn [_] (= 1 (@get-cnt "k1")))
             (fn [_] (= 1 (@set-cnt "k1")))
             (fn [_] (= 1 @load-cnt))))))

  (fact
    "about loader exception handling"
    (let [cache (atom {})
          set-cnt (atom {})
          get-cnt (atom {})
          load-cnt (atom 0)
          loader  #(go-try
                    (swap! load-cnt inc)
                    (throw (Exception.)))]
      (with-redefs [cache/rset (fn [k v & _]
                                (swap! cache assoc k v)
                                (swap! set-cnt (fn [set-cnt] (update-in set-cnt [k] #(inc (or % 0)))))
                                v)
                    cache/rget (fn [k & _]
                                 (swap! get-cnt (fn [get-cnt] (update-in get-cnt [k] #(inc (or % 0)))))
                                 (get @cache k))]
        (<!! (cache/rget-or-load> "k1" loader))
        => (every-checker
             (fn [res] (instance? Exception res))
             (fn [_] (= 1 (@get-cnt "k1")))
             (fn [_] (nil? (@set-cnt "k1")))
             (fn [_] (= 1 @load-cnt))))))

  (fact
    "about nil result from loader"
    (let [cache (atom {})
          set-cnt (atom {})
          get-cnt (atom {})
          load-cnt (atom 0)
          loader  #(go-try
                    (swap! load-cnt inc)
                    nil)]
      (with-redefs [cache/rset (fn [k v & _]
                                 (swap! cache assoc k v)
                                 (swap! set-cnt (fn [set-cnt] (update-in set-cnt [k] #(inc (or % 0)))))
                                v)
                    cache/rget (fn [k & _]
                                 (swap! get-cnt (fn [get-cnt] (update-in get-cnt [k] #(inc (or % 0)))))
                                 (get @cache k))]
        (<!! (cache/rget-or-load> "k1" loader))
        => (every-checker
             nil
             (fn [_] (= 1 (@get-cnt "k1")))
             (fn [_] (= 1 (@set-cnt "k1")))
             (fn [_] (= 1 @load-cnt)))))))

(facts
  "about locacl cache"

  (fact
    "about concurrent gets with loading"
    (let [load-cnt (atom 0)
          loader  #(go-try
                    (swap! load-cnt inc)
                    (<? (async/timeout 100))
                    "v1")]
      (-> [(cache/lget-or-load> "k1" loader 1)
           (cache/lget-or-load> "k1" loader 1)
           (do (<?? (async/timeout 50)) (cache/lget-or-load> "k1" loader 1))]
            <??*)
        => (every-checker
             (fn [res] (= res ["v1" "v1" "v1"]))
             (fn [_] (= 1 @load-cnt)))))

  (fact
    "about single get with loading"
    (let [load-cnt (atom 0)
          loader  #(go-try
                    (swap! load-cnt inc)
                    (<? (async/timeout 100))
                    "v1")]
      (<?? (cache/lget-or-load> "k2" loader 1))
      => (every-checker
           (fn [res] (= res "v1"))
           (fn [_] (= 1 @load-cnt)))))

  (fact
    "about loader exception handling"
    (let [load-cnt (atom 0)
          loader  #(go-try
                    (swap! load-cnt inc)
                    (throw (Exception.)))]
      (<!! (cache/lget-or-load> "k3" loader 1))
      => (every-checker
           (fn [res] (instance? Exception res))
           (fn [_] (= 1 @load-cnt)))))

  (fact
    "about nil result from loader"
    (let [load-cnt (atom 0)
          loader  #(go-try
                    (swap! load-cnt inc)
                    nil)]
      (<!! (cache/lget-or-load> "k4" loader 1))
      => (every-checker
           nil
           (fn [_] (= 1 @load-cnt))))))