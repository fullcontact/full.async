(defproject fullcontact/full.cache "0.4.5-SNAPSHOT"
  :description "In-memory + memcache caching for Clojure with async loading."

  :url "https://github.com/fullcontact/full.monty"

  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}  

  :deploy-repositories [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]                   

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [net.jodah/expiringmap "0.4.1"]
                 [net.spy/spymemcached "2.11.7"]
                 [com.taoensso/nippy "2.6.3"]
                 [fullcontact/full.core "0.4.5-SNAPSHOT"]
                 [fullcontact/full.async "0.4.5-SNAPSHOT"]]

  :plugins [[lein-midje "3.1.3"]]  

  :profiles {:dev {:dependencies [[midje "1.6.3"]]}})
