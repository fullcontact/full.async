(defproject fullcontact/camelsnake "0.8.15-SNAPSHOT"
  :description "String and keyword transformation between cases."

  :url "https://github.com/fullcontact/full.monty"

  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}

  :deploy-repositories [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]

  :java-source-paths ["src"]

  :dependencies [[org.clojure/clojure "1.7.0"]]

  :aot :all

  :plugins [[lein-midje "3.1.3"]]

  :profiles {:dev {:dependencies [[midje "1.7.0"]]}})
