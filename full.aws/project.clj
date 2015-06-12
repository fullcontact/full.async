(defproject fullcontact/full.aws "0.4.22"
  :description "Async Amazon Webservices client."

  :url "https://github.com/fullcontact/full.monty"

  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}

  :deploy-repositories [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.amazonaws/aws-java-sdk "1.10.0"]
                 [fullcontact/full.metrics "0.4.22"]
                 [fullcontact/full.http "0.4.22"]
                 [fullcontact/full.json "0.4.22"]
                 [fullcontact/full.async "0.4.22"]
                 [fullcontact/full.core "0.4.22"]]

  :aot :all

  :plugins [[lein-midje "3.1.3"]]

  :profiles {:dev {:dependencies [[midje "1.6.3"]]}})
