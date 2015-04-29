(ns full.rollbar.middleware
  (:require [clojure.core.async :refer [<!]]
            [full.http.client :refer [req>]]
            [full.async :refer [go-try]]
            [full.rollbar.core :as rollbar]
            [camelsnake.core :refer [->snake_case]]))

(defn report-exception>
  [handler>]
  (fn [req]
    (go-try
      (let [res (<! (handler> req))]
        (when (and @rollbar/enabled?
                   (instance? Throwable res)
                   (or (nil? (:status (ex-data res))) (>= (:status (ex-data res)) 500)))
          (rollbar/report> res req))
        res))))

