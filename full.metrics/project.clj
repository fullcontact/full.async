(defproject fullcontact/full.metrics "0.4.0-SNAPSHOT"
  :description "Clojure application metrics sugar for Riemann backend."

  :dependencies [[riemann-clojure-client "0.2.11"]
                 [com.climate/clj-newrelic "0.2.1"]                 
                 [fullcontact/full.core _]
                 [fullcontact/full.async _]]

  :plugins [[lein-modules "0.3.11"]])
