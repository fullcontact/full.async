(defproject fullcontact/full.json "0.1.2-SNAPSHOT"
  :description "Simple Clojure's wrapper for Google's libphonenumber."
  :url "https://github.com/fullcontact/full.json"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [cheshire "5.3.1"]
                 [fullcontact/full.core "0.1.0"]
                 [fullcontact/full.time "0.1.0"]
                 [fullcontact/camelsnake "0.1.2"]]
  :lein-release {:deploy-via :shell
                 :shell ["lein" "deploy" "clojars"]}
  :plugins [[lein-midje "3.1.3"]
            [lein-release "1.0.5"]]
  :profiles {:dev {:dependencies [[midje "1.6.3"]]}})
