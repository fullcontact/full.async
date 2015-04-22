(defproject fullcontact/full.cache "0.4.0"
  :description "In-memory + memcache caching for Clojure with async loading."

  :dependencies [[net.jodah/expiringmap "0.4.1"]
                 [clojurewerkz/spyglass "1.1.0"]
                 [com.taoensso/nippy "2.6.3"]
                 [fullcontact/full.core _]
                 [fullcontact/full.async _]]

  :plugins [[lein-modules "0.3.11"]])
