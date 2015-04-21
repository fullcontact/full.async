(defproject fullcontact/full.time leiningen.release/bump-version
  :description "clj-time add-on for simplified ISO-8601 format date/time handling."

  :dependencies [[clj-time "0.8.0"]
                 [fullcontact/full.core _]]

  :plugins [[lein-modules "0.3.11"]])
