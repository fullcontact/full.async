(ns full.dev
  "Debug helpers."
  (:require [full.core.log :as log]
            [clojure.string :as str]
            [clojure.stacktrace :as st]
            [ns-tracker.core :as tracker]))


(defn <println [& args]
  (apply prn args)
  (last args))

(defmacro catch-log
  [& body]
  `(try ~@body (catch Throwable e# (log/error e# "Exception") (throw e#))))

(def print-st st/print-stack-trace)

(defn call-stack []
  (try (throw (Exception. "")) (catch Exception e (print-st e))))

(def lc "Log context format helper"
  (partial format "%15s>"))


;;; Dynamic code reloading

(defn- check-namespace-changes [track]
  (doseq [ns-sym (track)]
    (try
      (log/info "Reloading namespace:" ns-sym)
      (require ns-sym :reload)
      (catch Throwable e
        (log/error (log/error e "Error reloading namespace:" ns-sym))))
    (Thread/sleep 500)))

(defn start-nstracker []
  (let [track (tracker/ns-tracker ["src" "checkouts"])]
    (doto
      (Thread.
        #(while true
          (check-namespace-changes track)))
      (.setDaemon true)
      (.start))))
