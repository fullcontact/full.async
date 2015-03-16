(defproject fullcontact/full.time "0.1.0"
  :description "Simple timestamp formatting and parsing for Clojure."
  :url "https://github.com/fullcontact/full.time"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-time "0.8.0"]
                 [fullcontact/full.core "0.1.0"]]
  :lein-release {:deploy-via :shell
                 :shell ["lein" "deploy" "clojars"]}
  :plugins [[lein-midje "3.1.3"]
            [lein-release "1.0.5"]]
  :profiles {:dev {:dependencies [[midje "1.6.3"]]}})
