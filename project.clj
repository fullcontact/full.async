(defproject fullcontact/full.monty "0.3.1-SNAPSHOT"
  :description "Minimalistic stack for building robust Clojure HTTP services."

  :url "https://github.com/fullcontact/full.monty"

  :dependencies [[org.clojure/clojure _]
                 [fullcontact/full.core _]
                 [fullcontact/full.time _]
                 [fullcontact/camelsnake _]
                 [fullcontact/full.json _]
                 [fullcontact/full.async _]
                 [fullcontact/full.dev _]]

  :plugins [[lein-modules "0.3.11"]]
            
  :modules {:subprocess nil
            :inherited
                {:deploy-repositories [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]
                 :url "https://github.com/fullcontact/full.monty"
                 :scm {:dir "."}
                 :license {:name "Eclipse Public License - v 1.0"
                           :url "http://www.eclipse.org/legal/epl-v10.html"
                           :distribution :repo}
                 :dependencies [[org.clojure/clojure _]]                         
                 :plugins [[lein-midje "3.1.3"]]}
            :versions 
                {org.clojure/clojure "1.6.0"
                 midje "1.6.3"
                 fullcontact/full.core :version
                 fullcontact/full.time :version
                 fullcontact/camelsnake :version
                 fullcontact/full.json :version
                 fullcontact/full.async :version
                 fullcontact/full.dev :version}}

  :profiles {:provided {:dependencies [[org.clojure/clojure _]]}
             :dev {:dependencies [[midje _]]}}

  :release-tasks  [["vcs" "assert-committed"]
                   ["change" "version" "leiningen.release/bump-version" "release"]
                   ["modules" "change" "version" "leiningen.release/bump-version" "release"]
                   ["vcs" "commit"]
                   ["vcs" "tag"]
                   ["modules" "deploy"]
                   ["deploy"]
                   ["change" "version" "leiningen.release/bump-version"]
                   ["modules" "change" "version" "leiningen.release/bump-version"]
                   ["vcs" "commit"]
                   ["vcs" "push"]])