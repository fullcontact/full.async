(ns full.liquibase
  (:require [clojure.java.jdbc :as jdbc])
  (:import (liquibase.database DatabaseFactory)
           (liquibase.database.jvm JdbcConnection)
           (liquibase Liquibase Contexts)
           (liquibase.resource ClassLoaderResourceAccessor)))

(defn- liquibase-db [con]
  (-> (DatabaseFactory/getInstance)
      (.findCorrectDatabaseImplementation (JdbcConnection. (:connection con)))))

(defn init-db
  [db-spec & {:keys [changelog contexts]
              :or {changelog "changelog.xml"
                   contexts "production"}}]
  (jdbc/with-db-connection [con db-spec]
                           (with-open [db (liquibase-db con)]
                             (-> (Liquibase. changelog (ClassLoaderResourceAccessor.) db)
                                 (.update (Contexts. contexts))))))
