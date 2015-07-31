(ns full.metrics.newrelic
  (:require [full.async :refer [<? go-try]])
  (:import (com.newrelic.api.agent NewRelic Request Response Trace)))

(def is-enabled? (some? (System/getProperty "newrelic.config.app_name")))

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

(defn ^{:newrelic-method-annotations {Trace {:dispatcher true}}} tracerAsync
  "Named tracerAsync since the new relic extension expects java compliant names. Simple async function that sets
  request/response data on new relic tx. No dive down into mysql queries, etc yet."
  [handler> request]
  (go-try
    (let [response (<? (handler> request))
          nr-request (newrelic-request request)
          nr-response (newrelic-response response)]
      (when is-enabled?
         (NewRelic/setRequestAndResponse nr-request nr-response))
      response)))

(defn trace>
  [handler>]
  (fn [request]
    (go-try
      (<? (tracerAsync handler> request)))))

(defn report-error
  "Sends any sort of error to the New Relic agent. We expect the agent
  calls to return quickly and that New Relic doesn't block us if there
  is an issue communicating with the mother ship. Properly handles
  exceptions with ex-data."
  [ex]
  (if-let [ex-data (ex-data ex)]
    (NewRelic/noticeError ex ex-data)
    (NewRelic/noticeError ex)))
