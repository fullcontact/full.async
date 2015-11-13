(defproject fullcontact/full.monty "0.8.18"
  :description "Minimalistic stack for building robust Clojure HTTP services."

  :url "https://github.com/fullcontact/full.monty"

  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}

  :plugins [[lein-sub "0.3.0"]
            [lein-set-version "0.4.1"]]

  :sub ["full.core"
        "full.dev"
        "camelsnake"
        "full.time"
        "full.json"
        "full.edn"
        "full.async"
        "full.metrics"
        "full.cache"
        "full.http"
        "full.bootstrap"
        ; optional modules
        "full.rollbar"
        "full.rabbit"
        "full.liquibase"
        "full.aws"]

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