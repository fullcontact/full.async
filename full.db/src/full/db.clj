(ns full.db
  (:require [clojure.core.async :refer [chan >!! close!]]
            [clojure.core.async.impl.concurrent :refer [counted-thread-factory]]
            [korma.db :refer [default-connection create-db mysql with-db] :as kdb]
            [korma.config :refer [extract-options]]
            [hikari-cp.core :refer [make-datasource]]
            [full.core.config :refer [opt]]
            [full.metrics :refer [timeit gauge]])
  (:import (java.util.concurrent Executors)))

; See https://github.com/tomekw/hikari-cp#configuration-options
; for descriptiong of config options.
(def adapter (opt [:db :adapter] :default "mysql"))
(def server-name (opt [:db :server-name] :default nil))
(def database-name (opt [:db :database-name] :default nil))
(def port-number (opt [:db :port-number] :default nil))
(def username (opt [:db :username] :default nil))
(def password (opt [:db :password] :default nil))
(def auto-commit (opt [:db :auto-commit] :default true))
(def read-only (opt [:db :read-only] :default false))
(def connection-test-query (opt [:db :connection-test-query] :default nil))
(def connection-timeout (opt [:db :connection-timeout] :default 30000))
(def validation-timeout (opt [:db :validation-timeout] :default 5000))
(def idle-timeout (opt [:db :idle-timeout] :default 600000))
(def max-lifetime (opt [:db :max-lifetime] :default 1800000))
(def minimum-idle (opt [:db :minimum-idle] :default 10))
(def maximum-pool-size (opt [:db :maximum-pool-size] :default 10))
(def pool-name (opt [:db :pool-name] :default nil))
(def leak-detection-threshold (opt [:db :leak-detection-threshold] :default 0))
(def register-mbeans (opt [:db :register-mbeans] :default false))
(def data-source-properties (opt [:db :properties] :default {}))

(def db-specs {"firebird" kdb/firebird
               "postgres" kdb/postgres
               "oracle" kdb/oracle
               "mysql" kdb/mysql
               "vertica" kdb/vertica
               "mssql" kdb/mssql
               "msaccess" kdb/msaccess
               "sqlite3" kdb/sqlite3
               "h2" kdb/h2})

(defn- create-connection []
  (let [spec (or (get db-specs @adapter)
                 (throw (RuntimeException. (str "Unsupported database adapter: "
                                                @adapter))))
        config {:adapter @adapter
                :server-name @server-name
                :database-name @database-name
                :port-number @port-number
                :username @username
                :password @password
                :auto-commit @auto-commit
                :read-only @read-only
                :connection-test-query @connection-test-query
                :connection-timeout @connection-timeout
                :validation-timeout @validation-timeout
                :idle-timeout @idle-timeout
                :max-lifetime @max-lifetime
                :minimum-idle @minimum-idle
                :maximum-pool-size @maximum-pool-size
                :pool-name @pool-name
                :leak-detection-threshold @leak-detection-threshold
                :register-mbeans @register-mbeans}
        ds (make-datasource config)]
    (doseq [[prop val] @data-source-properties]
      (.addDataSourceProperty ds (name prop)  val))
    (default-connection
      {:pool {:datasource ds}
       :options (extract-options (spec {}))})))

(def db (delay (create-connection)))

(defn get-connection []
  (kdb/get-connection @db))

(defmacro do
  [& body]
  `(with-db @db ~@body))

(def thread-macro-executor
  (delay (Executors/newFixedThreadPool
           @maximum-pool-size
           (counted-thread-factory "db-thread-%d" true))))

(defn thread-call
  [f]
  (let [c (chan 1)]
    (try
      (let [binds (clojure.lang.Var/getThreadBindingFrame)]
        (.execute @thread-macro-executor
                  (fn []
                    (clojure.lang.Var/resetThreadBindingFrame binds)
                    (try
                      (let [ret (f)]
                        (when-not (nil? ret)
                          (>!! c ret)))
                      (finally
                        (close! c))))))
      (catch Exception e
        (>!! c e)
        (close! c)))
    c))

(def do-async-gauge (atom 0))

(defmacro do-async
  [& body]
  `(let [g# {:service "full.db.do-async/gauge"}]
     (gauge g# (swap! do-async-gauge inc))
     (thread-call
       (fn []

         (try
           (with-db @db ~@body)
           (catch Throwable e# e#)
           (finally
             (gauge g# (swap! do-async-gauge dec))))))))

(defmacro do-async-timeit
  [event & body]
  `(do-async
     (timeit {:service ~event
              :tags ["db"]}
             ~@body)))
