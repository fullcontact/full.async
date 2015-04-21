(defproject fullcontact/full.dev leiningen.release/bump-version
  :description "Clojure's development and debugging helpers"

  :dependencies [[ns-tracker "0.2.2"]
                 [fullcontact/full.core _]]

  :plugins [[lein-modules "0.3.11"]])