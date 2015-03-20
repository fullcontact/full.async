(defproject fullcontact/full.dev "0.1.2-SNAPSHOT"
  :description "Clojure's development and debugging helpers"
  :url "https://github.com/fullcontact/full.dev"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [ns-tracker "0.2.2"]
                 [fullcontact/full.core "0.1.0"]]
  :lein-release {:deploy-via :shell
                 :shell ["lein" "deploy" "clojars"]}
  :plugins [[lein-release "1.0.5"]])
