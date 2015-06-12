(ns full.time
  (:require [clojure.instant :as i]
            [clj-time.core :as tt]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc]
            [full.core.log :as log])
  (:import (org.joda.time.format DateTimeFormat)
           (java.util Locale)
           (org.joda.time.tz FixedDateTimeZone)
           (org.joda.time DateTime)
           (java.io Writer)))


;;; date time helpers ;;;


(def iso-ts-formatter (tf/formatters :date-time))
(def iso-ts-formatter-no-ms (tf/formatters :date-time-no-ms))
(def iso-ts-formatter-no-ms-tz (tf/formatters :date-hour-minute-second))
(def iso-d-formatter (tf/formatters :date))
(def iso-year-month-formatter (tf/formatters :year-month))
(def iso-year-formatter (tf/formatters :year))
(def rfc822-formatter (-> (DateTimeFormat/forPattern "EEE, dd MMM yyyy HH:mm:ss z")
                          (.withLocale Locale/US)
                          (.withZone (FixedDateTimeZone. "UTC" "UTC" 0 0))))

(defn dt<-iso-ts [ts]
  (when ts
    (try
      (tf/parse iso-ts-formatter ts)
      (catch Exception _
        (try
          (tf/parse iso-ts-formatter-no-ms ts)
          (catch Exception _
            (try
              (tf/parse iso-ts-formatter-no-ms-tz ts)
              (catch Exception e
                (log/error "Error parsing timestamp" ts (str e))))))))))

(defn dt->iso-ts
  "Creates a DateTime object from ISO timestamp."
  [dt]
  (when dt (tf/unparse iso-ts-formatter dt)))

(defn dt<-rfc822-ts
  [ts]
  (when ts
    (try
      (tf/parse rfc822-formatter ts)
      (catch Exception e
        (log/error "Error parsing timestamp" ts (str e))))))

(defn dt->rfc822-ts
  [dt]
  (when dt (tf/unparse rfc822-formatter dt)))

(defn d<-iso-d [ts]
  (when ts
    (try
      (cond
        (= ts "0000-00") nil  ;; special handling for bad data
        ; --mm-dd
        (.startsWith ts "--") (tf/parse-local-date iso-d-formatter (str "0000" (.substring ts 1)))
        ; mm-dd
        (= 5 (.length ts)) (tf/parse-local-date iso-d-formatter (str "0000-" ts))
        ; yyyy
        (= 4 (.length ts)) (tf/parse-local-date iso-year-formatter ts)
        ; yyyy-mm
        (= 7 (.length ts)) (tf/parse-local-date iso-year-month-formatter ts)
        ; yyyy-mm-dd
        :else (tf/parse-local-date iso-d-formatter ts))
      (catch Exception e
        (log/error "Error parsing timestamp" ts (str e))))))

(defn d->iso-d [ts]
  (when ts (tf/unparse-local-date iso-d-formatter ts)))

(defn dt<-epoch
  "Creates a DateTime object from a (long) unix timestamp"
  [epoch]
  (tc/from-long epoch))

(defn epoch<-dt
  "Creates a DateTime object from a (long) unix timestamp"
  [dt]
  (tc/to-long dt))

(def now-utc tt/now)

(defn dt-between [dt from to]
  (tt/within? (tt/interval from to) dt))


;;; (DE)SERIALZIATION


(defmethod print-method DateTime
  [^DateTime d ^Writer w]
  (#'i/print-date (.toDate d) w))

(defmethod print-dup DateTime
  [^DateTime d ^Writer w]
  (#'i/print-date (.toDate d) w))

(defn construct-dt [years months days hours minutes seconds nanoseconds
                    offset-sign offset-hours offset-minutes]
  (DateTime. (.getTimeInMillis (#'i/construct-calendar years months days
                                 hours minutes seconds 0
                                 offset-sign offset-hours offset-minutes))))
(def read-instant-dt
  "To read an instant as an org.joda.time.DateTime, bind *data-readers* to a
map with this var as the value for the 'inst key."
  (partial i/parse-timestamp (i/validated construct-dt)))

(defmacro with-dt-reader [& body]
  `(binding [*data-readers* (assoc *data-readers* '~'inst read-instant-dt)]
     ~@body))
