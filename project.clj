(defproject fullcontact/full.async "0.9.2-SNAPSHOT"
  :description "Extensions and helpers for core.async."
  :url "https://github.com/fullcontact/full.async"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :deploy-repositories [["releases" {:url "https://clojars.org/repo/" :creds :gpg}]]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.293"]
                 [org.clojure/core.async "0.2.395"]]
  :aliases {"at" ["test-refresh"]
            "ats" ["do" "clean," "cljsbuild" "auto" "test"]}
  :cljsbuild {:test-commands {"test" ["phantomjs" :runner "target/test.js"]}
              :builds [{:id "test"
                        :notify-command ["phantomjs" :cljs.test/runner "target/test.js"]
                        :source-paths ["src" "test"]
                        :compiler {:output-to "target/test.js"
                                   :optimizations :whitespace
                                   :pretty-print true}}]}
  :aot :all
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "--no-sign"]
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]
  :profiles {:dev {:plugins [[com.jakemccrary/lein-test-refresh "0.17.0"]
                             [lein-cljsbuild "1.1.4"]
                             [com.cemerick/clojurescript.test "0.3.3"]]}})
