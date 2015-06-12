(ns full.t_time
  (:require [midje.sweet :refer :all]
            [full.time :refer :all]
            [clj-time.core :as t]))


(facts
  "about ISO date and time parsing"
  (dt<-iso-ts "2014-01-02T03:04:05Z") => (t/date-time 2014 1 2 3 4 5)
  (dt<-iso-ts "2014-01-02T03:04:04Zdsfffsdf") => nil
  (dt<-iso-ts "2014-01-02T03:04:05.678Z") => (t/date-time 2014 1 2 3 4 5 678)
  (dt->iso-ts (t/date-time 2014 1 2 3 4 5 678)) => "2014-01-02T03:04:05.678Z"
  (dt->iso-ts (t/date-time 2014 1 2 3 4 5)) => "2014-01-02T03:04:05.000Z"
  (dt<-rfc822-ts "Fri, 12 Jun 2015 17:50:00 UTC") => (t/date-time 2015 6 12 17 50 0 0)
  (d<-iso-d "2014-01-02") => (t/local-date 2014 1 2)
  (d<-iso-d "0000-01-02") => (t/local-date 0 1 2)
  (d<-iso-d "--01-02") => (t/local-date 0 1 2)
  (d<-iso-d "12-26") => (t/local-date 0 12 26)
  (d<-iso-d "2014-02") => (t/local-date 2014 2 1)
  (d<-iso-d "2014") => (t/local-date 2014 1 1)
  (d->iso-d (t/local-date 2014 1 2)) => "2014-01-02"
  (d->iso-d (t/local-date 0 1 2)) => "0000-01-02")
