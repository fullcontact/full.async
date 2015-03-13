(defproject fullcontact/camelsnake "0.1.1-SNAPSHOT"
  :description "String and keyword transformation between cases."
  :url "https://github.com/fullcontact/camelsnake"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :dependencies [[org.clojure/clojure "1.6.0"]]
  :lein-release {:deploy-via :shell
                 :shell ["lein" "deploy" "clojars"]}
  :plugins [[lein-midje "3.1.3"]
            [lein-release "1.0.5"]]
  :profiles {:dev {:dependencies [[midje "1.6.3"]]}})
