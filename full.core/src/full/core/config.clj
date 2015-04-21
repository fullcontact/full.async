(ns full.core.config
  (:require [full.core.sugar :refer :all]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.io :refer [as-file]]
            [clj-yaml.core :as yaml])
  (:gen-class))

(defonce _config (atom {}))

(def config-cli-options
  [["-c" "--config name" "Config filename"]])

(defn config-file []
  (let [f (-> ; allow to override via  command line
            (:config (:options (parse-opts *command-line-args* config-cli-options)))
            ; or env variable
            (or (System/getenv "FULL_CONFIG"))
            ; legacy fallback - deprecated
            (or (System/getenv "FOX_CONFIG"))
            ; of use dev.yaml as default
            (or "dev.yaml")
            (as-file))]
    (when (not (.exists f))
      (println "full.core.config - EXITING! - Configuration file" (.getAbsolutePath f) "not found.")
      (System/exit 1))
    (println "full.core.config - Using config file" (.getAbsolutePath f))
    f))

(defn normalize-config [config]
  (map-map keyword config))

(defn configure
  ([config] (reset! _config (normalize-config config)))
  ([] (swap! _config (fn [config]
                       ; only load config once
                       (if (empty? config)
                         (-> (config-file)
                             (slurp)
                             (yaml/parse-string)
                             (normalize-config))
                         ; else - already configured, return exisiting config
                         config)))))

(defn get-config [config & ks]
  (let [v (get-in config ks)]
    (when (nil? v)
      (throw (RuntimeException. (str "Property " ks " is not configured"))))
    v))

(defn config [k & ks]
  (let [v (apply get-config (into [@_config k] ks))]
    (if (map? v)
      (partial get-config v)
      v)))

(defn config? [k & ks]
  (get-in @_config (into [k] ks)))

(defmacro defconfig
  "Define required configuration property, for example (defconfig some-prop :some-prop). Defined properties should be
  dereferenced with @ when used, for example @some-prop. When property is not configured, dereferencing it will throw
  an exception."
  [name & ks]
  `(def ~name (delay (get-config @_config ~@ks))))

(defmacro defoptconfig
  "Define optional configuration property, for example (defoptconfig some-prop :some-prop). Defined properties should be
  dereferenced with @ when used, for example @some-prop. When property is not configured, will return nil."
  [name & ks]
  `(def ~name (delay (config? ~@ks))))

(defmacro defmappedconfig
  "Define a configuration property, for example (defmappedconfig some-prop f :some-prop).
   On creation will be passed as an argument to f, (for example, to create a set).
   Defined properties should be dereferenced with @ when used, for example @some-prop.
   When property is not configured, will return nil."
  [name f & ks]
  `(def ~name (delay (~f (config? ~@ks)))))
