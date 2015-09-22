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
  "DEPRECATED: Use 'opt' instead.
  Define required configuration property, for example (defconfig some-prop :some-prop). Defined properties should be
  dereferenced with @ when used, for example @some-prop. When property is not configured, dereferencing it will throw
  an exception."
  {:deprecated "0.5.7"}
  [name & ks]
  `(def ~name (delay (get-config @_config ~@ks))))

(defmacro defoptconfig
  "DEPRECATED: Use 'opt' instead.
  Define optional configuration property, for example (defoptconfig some-prop :some-prop). Defined properties should be
  dereferenced with @ when used, for example @some-prop. When property is not configured, will return nil."
  {:deprecated "0.5.7"}
  [name & ks]
  `(def ~name (delay (config? ~@ks))))

(defmacro defmappedconfig
  "DEPRECATED: Use 'opt' instead.
  Define a configuration property, for example (defmappedconfig some-prop f :some-prop).
  On creation will be passed as an argument to f, (for example, to create a set).
  Defined properties should be dereferenced with @ when used, for example @some-prop.
  When property is not configured, will return nil."
  {:deprecated "0.5.7"}
  [name f & ks]
  `(def ~name (delay (~f (config? ~@ks)))))

::undefined

(defn opt
  "Yields a lazy configuration value, readable by dereferencing with @. Will
  throw an exception when no value is present in configuration and no default
  value is specified.

  Parameters:
    sel - a keyword or vector of keywords representing path in config file
    :default - default value. Use `nil` for optional configuration.
    :mapper -  function to apply to configuration value before returning
  "
  [sel & {:keys [default mapper]
          :or {default ::undefined}}]
  {:pre [(or (keyword? sel)
             (and (vector? sel)
                  (every? keyword sel)))
         (or (nil? mapper)
             (fn? mapper))]}
  (delay
    (let [conf-value (if (vector? sel) (get-in @_config sel) (get @_config sel))
          value (if (some? conf-value) conf-value default)]
      (when (= ::undefined value)
        (throw (RuntimeException. (str "Option " sel " is not configured"))))
      (if mapper
        (mapper value)
        value))))
