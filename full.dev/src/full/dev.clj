(ns full.dev
  "Debug and development helpers."
  (:require [full.core.log :as log]
            [clojure.string :as s]
            [clojure.stacktrace :as st]
            [clojure.edn :as edn]
            [clojure.pprint :refer [pprint]]
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


;;; File I/O

(defn slurp-edn [f & opts]
  (edn/read-string (apply slurp f opts)))

(defn slurp-lines [f & opts]
  (s/split (apply slurp f opts) #"\n"))

(defn spit-pprint [f content & opts]
  (with-open [^java.io.Writer writer (apply clojure.java.io/writer f opts)]
    (pprint content writer)))


;;; Dynamic code reloading

(defn- check-namespace-changes [track]
  (doseq [ns-sym (track)]
    (try
      (log/info "Reloading namespace:" ns-sym)
      (require ns-sym :reload)
      (catch Throwable e
        (log/error (log/error "Error reloading namespace" ns-sym e)))))
  (Thread/sleep 500))

(defn start-nstracker
  "Automatically tracks source code changes in src and checkouts folder
  and reloads the changed namespaces."
  []
  (let [track (tracker/ns-tracker ["src" "checkouts"])]
    (doto
      (Thread.
        #(while true
          (check-namespace-changes track)))
      (.setDaemon true)
      (.start))))
