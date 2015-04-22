(defproject fullcontact/full.monty "0.4.2-SNAPSHOT"
  :description "Minimalistic stack for building robust Clojure HTTP services."

  :url "https://github.com/fullcontact/full.monty"

  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}  

  :deploy-repositories [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]                 

  :dependencies [[org.clojure/clojure "1.6.0"]
                 ; base modules only
                 [fullcontact/full.core "0.4.2-SNAPSHOT"]
                 [fullcontact/full.time "0.4.2-SNAPSHOT"]
                 [fullcontact/camelsnake "0.4.2-SNAPSHOT"]
                 [fullcontact/full.json "0.4.2-SNAPSHOT"]
                 [fullcontact/full.async "0.4.2-SNAPSHOT"]
                 [fullcontact/full.dev "0.4.2-SNAPSHOT"]
                 [fullcontact/full.cache "0.4.2-SNAPSHOT"]
                 [fullcontact/full.metrics "0.4.2-SNAPSHOT"]
                 [fullcontact/full.http "0.4.2-SNAPSHOT"]]

  :plugins [[lein-sub "0.3.0"]
            [lein-set-version "0.4.1"]]

  :sub ["full.core"
        "full.dev"
        "camelsnake" 
        "full.time" 
        "full.json" 
        "full.async" 
        "full.metrics"
        "full.cache"
        "full.http"
        "full.rabbit"]

  :release-tasks  [["vcs" "assert-committed"]
                   ["set-version"]
                   ["sub" "set-version"]
                   ["vcs" "commit"]
                   ["vcs" "tag"]
                   ["sub" "deploy"]
                   ["set-version" ":point"]
                   ["sub" "set-version" ":point"]
                   ["vcs" "commit"]
                   ["vcs" "push"]])