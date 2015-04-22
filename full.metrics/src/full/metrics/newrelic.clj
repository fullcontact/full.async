(ns full.metrics.newrelic
  (:require [com.climate.newrelic.trace :refer [defn-traced]]
            [full.async :refer [<? go-try]])
  (:import (com.newrelic.api.agent NewRelic Request Response)))

(defn get-newrelic-app-name
  "Returns the value of System property -Dnewrelic.config.app_name if
  it is defined, nil if it is not."
  []
  (System/getProperty "newrelic.config.app_name"))

(defn is-enabled?
  "Checks the JVM args to see if New Relic monitoring is enabled.
  Returns truthy/falsey."
  []
  (get-newrelic-app-name))

(defn newrelic-request
  "Takes a Ring request and returns a com.newrelic.api.agent.Request."
  [ring-request]
  (reify Request
    (getRequestURI [this] (:uri ring-request))
    (getRemoteUser [this] (:remote-addr ring-request))
    (getParameterNames [this] (sort (:params ring-request)))
    (getParameterValues [this param] (get ring-request (keyword param)))
    (getAttribute [this attr] (get ring-request (keyword attr)))
    ;; Don't support cookies unless we need them, there no sense in
    ;; adding an extra middleware to every request, as in
    ;; ring.middleware.cookies/wrap-cookies
    (getCookieValue [this cookie] nil)))

(defn newrelic-response
  "Takes a Ring response and returns a com.newrelic.api.agent.Response."
  [ring-response]
  (reify Response
    (getStatus [this] (:status ring-response))
    ;; Look it up yourself lazybones
    (getStatusMessage [this] nil)
    (getContentType [this] (-> ring-response :headers :content-type))))

(defn-traced trace-async>
  "Middleware for reporting web requests to New Relic."
  [handler>]
  (fn [request]
    (go-try
     (let [response (<? (handler> request))
           nr-request (newrelic-request request)
           nr-response (newrelic-response response)]
       (when (is-enabled?)
         (NewRelic/setRequestAndResponse nr-request nr-response))
       response))))

(defn report-error
  "Sends any sort of error to the New Relic agent. We expect the agent
  calls to return quickly and that New Relic doesn't block us if there
  is an issue communicating with the mother ship. Properly handles
  exceptions with ex-data."
  [ex]
  (let [ex-data ex]
    (if ex-data
      (NewRelic/noticeError ex ex-data)
      (NewRelic/noticeError ex))))
