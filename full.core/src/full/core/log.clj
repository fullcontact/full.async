(ns full.core.log
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :refer [as-file as-url]]
            [full.core.config :refer [opt]])
  (:import (org.slf4j MDC)))

(def log-config (opt :log-config :default "log.xml"))

(def ^:dynamic context "")

(defmacro with-prefix
  [context & body]
  `(binding [context ~context]
     ~@body))

(defmacro with-mdc
  "Use this to add a map to any logging wrapped in the macro. Macro can be nested.
  Note that this won't work well across go blocks since execution switches between
  threads and doesn't pass through thead locals.
  (with-mdc {:key \"value\"} (log/info \"yay\"))"
  [context & body]
  `(let [wrapped-context# ~context
         ctx# (MDC/getCopyOfContextMap)]
     (try
       (if (map? wrapped-context#)
         (doall (map (fn [[k# v#]] (MDC/put (name k#) (str v#))) wrapped-context#)))
       ~@body
       (finally
         (if ctx#
           (MDC/setContextMap ctx#)
           (MDC/clear))))))

(defmacro trace [& args]
  `(log/trace context ~@args))

(defmacro debug [& args]
  `(log/debug context ~@args))

(defmacro info [& args]
  `(log/info context ~@args))

(defmacro warn [& args]
  `(log/warn context ~@args))

(defmacro error [x & more]
  `(let [x# ~x]
     (if (instance? Throwable x#)
       (log/error x# context ~@more)
       (log/error context x# ~@more))))

(defmacro level-enabled? [level]
  `(log/enabled? ~level))

(defn do-info
  "Logs all arguments except the last one, evaluates last one, logs it with
   info loglevel and returns it's value."
  [& args]
    (info (clojure.string/join ", " args))
    (last args))

(defn do-debug
  "Logs all arguments except the last one, evaluates last one, logs it with
   debug loglevel and returns it's value."
  [& args]
    (debug (clojure.string/join ", " args))
    (last args))


;;; Configuration

(defn check-config-file [config-file]
  (let [f (as-file config-file)]
    (when (not (.exists f))
      (println "full.core.log - EXITING!- Log configuration file" (.getAbsolutePath f) "not found.")
      (System/exit 1))
    (println "full.core.log - Using log config file" (.getAbsolutePath f))
    f))

(defn configure
  ; TODO - if we make this macro, would it be possible to load this module without the logback dependency?
  ([] (configure @log-config))
  ([config-file]
   (let [context (org.slf4j.LoggerFactory/getILoggerFactory)]
     (try
       (let [configurator (ch.qos.logback.classic.joran.JoranConfigurator.)]
         (.setContext configurator context)
         (.reset context)
         (.doConfigure configurator (as-url (check-config-file config-file))))
       (catch Exception _))  ; StatusPrinter will handle this
     (ch.qos.logback.core.util.StatusPrinter/printInCaseOfErrorsOrWarnings context))))
