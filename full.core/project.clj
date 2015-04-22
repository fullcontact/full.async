(defproject fullcontact/full.core "0.4.2-SNAPSHOT"
  :description "FullContact's core Clojure library - logging, configuration and common helpers."
    
  :dependencies [[org.clojure/tools.cli "0.3.1"]
                 [org.clojure/tools.logging "0.3.0"]
                 [me.moocar/logback-gelf "0.10p1"]
                 [ch.qos.logback/logback-classic "1.1.2"]
                 [clj-yaml "0.4.0"]]

  :plugins [[lein-modules "0.3.11"]])
