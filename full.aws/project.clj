(defproject fullcontact/full.aws "0.5.4-SNAPSHOT"
  :description "Async Amazon Webservices client."

  :url "https://github.com/fullcontact/full.monty"

  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}

  :deploy-repositories [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.amazonaws/aws-java-sdk "1.10.0"]
                 [com.taoensso/faraday "1.7.1" ; DynamoDB sugar
                  :exclusions [com.amazonaws/aws-java-sdk-dynamodb joda-time]]
                 ; include full.time for joda-time dependency
                 [fullcontact/full.time "0.5.4-SNAPSHOT"]
                 [fullcontact/full.metrics "0.5.4-SNAPSHOT"]
                 [fullcontact/full.http "0.5.4-SNAPSHOT"]
                 [fullcontact/full.json "0.5.4-SNAPSHOT"]
                 [fullcontact/full.edn "0.5.4-SNAPSHOT"]
                 [fullcontact/full.async "0.5.4-SNAPSHOT"]
                 [fullcontact/full.core "0.5.4-SNAPSHOT"]]

  :aot :all

  :plugins [[lein-midje "3.1.3"]]

  :profiles {:dev {:dependencies [[midje "1.6.3"]]}})
