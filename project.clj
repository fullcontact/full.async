(defproject fullcontact/full.async "1.1.0"
  :description "Extensions and helpers for core.async."
  :url "https://github.com/fullcontact/full.async"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :deploy-repositories [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.597"]
                 [org.clojure/core.async "0.7.559"]]
  :aliases {"at" ["test-refresh"]
            "ats" ["doo" "phantom"]}
  :aot :all
  :cljsbuild {:builds {:test {:source-paths ["src" "test"]
                              :compiler {:output-to "target/test.js"
                                         :main 'full.test-runner
                                         :optimizations :simple
                                         :pretty-print true}}}}
  :doo {:build "test"}
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "--no-sign"]
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]
  :profiles {:dev {:plugins [[lein-cljsbuild "1.1.4"]
                             [lein-doo "0.1.7"]]}})
