(defproject fullcontact/full.json "0.4.1-SNAPSHOT"
  :description "Read and write JSON (Cheshire extension)."

  :dependencies [[cheshire "5.3.1"]
                 [fullcontact/full.core _]
                 [fullcontact/full.time _]
                 [fullcontact/camelsnake _]]
  
  :plugins [[lein-modules "0.3.11"]])
