(defproject fullcontact/full.http "0.4.0"
  :description "Async HTTP client and server on top of http-kit and core.async."

  :dependencies [[http-kit "2.1.18"]
                 [compojure "1.3.1"]
                 [javax.servlet/servlet-api "2.5"]
                 [fullcontact/camelsnake _]  
                 [fullcontact/full.json _]
                 [fullcontact/full.metrics _]
                 [fullcontact/full.core _]
                 [fullcontact/full.async _]]

  :plugins [[lein-modules "0.3.11"]])
