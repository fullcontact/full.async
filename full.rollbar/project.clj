(defproject fullcontact/full.rollbar "0.4.11"
  :description "Library to ship exceptions and request information to the rollbar logging service."

  :url "https://github.com/fullcontact/full.monty"

  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}

  :deploy-repositories [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [fullcontact/camelsnake "0.4.9"]
                 [fullcontact/full.json "0.4.9"]
                 [fullcontact/full.metrics "0.4.9"]
                 [fullcontact/full.async "0.4.9"]
                 [fullcontact/full.core "0.4.9"]
                 [fullcontact/full.http "0.4.9"]]
  :aot :all

  :plugins [[lein-midje "3.1.3"]]

  :profiles {:dev {:dependencies [[midje "1.6.3"]]}})

