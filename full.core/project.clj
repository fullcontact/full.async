(defproject fullcontact/full.core "0.5.10-SNAPSHOT"
  :description "FullContact's core Clojure library - logging, configuration and common helpers."

  :url "https://github.com/fullcontact/full.monty"

  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}

  :deploy-repositories [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/tools.logging "0.3.0"]
                 [me.moocar/logback-gelf "0.10p1"]
                 [ch.qos.logback/logback-classic "1.1.2"]
                 [org.slf4j/jul-to-slf4j "1.7.0"]
                 [clj-yaml "0.4.0"]
                 [commons-codec/commons-codec "1.10"]]

  :aot :all

  :plugins [[lein-midje "3.1.3"]]

  :profiles {:dev {:dependencies [[midje "1.6.3"]]}})
