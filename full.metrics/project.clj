(defproject fullcontact/full.metrics "0.4.15-SNAPSHOT"
  :description "Clojure application metrics sugar for Riemann backend."

  :url "https://github.com/fullcontact/full.monty"

  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}

  :deploy-repositories [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [riemann-clojure-client "0.2.11"]
                 [com.climate/clj-newrelic "0.2.1"]
                 [fullcontact/full.async "0.4.15-SNAPSHOT"]
                 [fullcontact/full.core "0.4.15-SNAPSHOT"]]

  :aot :all

  :plugins [[lein-midje "3.1.3"]]

  :profiles {:dev {:dependencies [[midje "1.6.3"]]}})