(defproject fullcontact/full.json "0.7.5-SNAPSHOT"
  :description "Read and write JSON (Cheshire extension)."

  :url "https://github.com/fullcontact/full.monty"

  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}

  :deploy-repositories [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [cheshire "5.5.0"]
                 [fullcontact/full.time "0.7.5-SNAPSHOT"]
                 [fullcontact/camelsnake "0.7.5-SNAPSHOT"]
                 [fullcontact/full.core "0.7.5-SNAPSHOT"]]

  :aot :all

  :plugins [[lein-midje "3.1.3"]]

  :profiles {:dev {:dependencies [[midje "1.6.3"]]}})
