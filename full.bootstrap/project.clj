(defproject fullcontact/full.bootstrap "0.4.23"
  :description "Boostrap module that pulls in all commonly used full-monty dependencies."

  :url "https://github.com/fullcontact/full.monty"

  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}

  :deploy-repositories [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [fullcontact/full.core "0.4.23"]
                 [fullcontact/full.time "0.4.23"]
                 [fullcontact/camelsnake "0.4.23"]
                 [fullcontact/full.json "0.4.23"]
                 [fullcontact/full.async "0.4.23"]
                 [fullcontact/full.dev "0.4.23"]
                 [fullcontact/full.cache "0.4.23"]
                 [fullcontact/full.metrics "0.4.23"]
                 [fullcontact/full.http "0.4.23"]]

  :aot :all

  :profiles {:dev {:dependencies [[midje "1.6.3"]]}})
