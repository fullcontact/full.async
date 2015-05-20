(defproject fullcontact/full.http "0.4.15-SNAPSHOT"
  :description "Async HTTP client and server on top of http-kit and core.async."

  :url "https://github.com/fullcontact/full.monty"

  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}

  :deploy-repositories [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [http-kit "2.1.18"]
                 [compojure "1.3.1"]
                 [javax.servlet/servlet-api "2.5"]
                 [ring-cors "0.1.7"]
                 [fullcontact/camelsnake "0.4.15-SNAPSHOT"]
                 [fullcontact/full.json "0.4.15-SNAPSHOT"]
                 [fullcontact/full.metrics "0.4.15-SNAPSHOT"]
                 [fullcontact/full.async "0.4.15-SNAPSHOT"]
                 [fullcontact/full.core "0.4.15-SNAPSHOT"]]

  :aot :all

  :plugins [[lein-midje "3.1.3"]]

  :profiles {:dev {:dependencies [[midje "1.6.3"]]}})
