(defproject fullcontact/full.rabbit "0.8.13"
  :description "RabbitMQ sugar on top of langohr."

  :url "https://github.com/fullcontact/full.monty"

  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}

  :deploy-repositories [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [com.novemberain/langohr "3.2.0"
                  :exclusions [cheshire]]
                 [fullcontact/full.metrics "0.8.13"]
                 [fullcontact/full.json "0.8.13"]
                 [fullcontact/full.async "0.8.13"]
                 [fullcontact/full.core "0.8.13"]]

  :aot :all

  :plugins [[lein-midje "3.1.3"]]

  :profiles {:dev {:dependencies [[midje "1.6.3"]]}})
