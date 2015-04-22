(defproject fullcontact/full.rabbit "0.4.1"
  :description "RabbitMQ sugar on top of langohr."

  :dependencies [[com.novemberain/langohr "3.0.0-rc2"]
                 [fullcontact/full.metrics _]
                 [fullcontact/full.json _]
                 [fullcontact/full.async _]
                 [fullcontact/full.core _]]

  :plugins [[lein-modules "0.3.11"]])
