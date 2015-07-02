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
  (d<-iso-d "2014-01-02") => (t/local-date 2014 1 2)
  (d<-iso-d "0000-01-02") => (t/local-date 0 1 2)
  (d<-iso-d "--01-02") => (t/local-date 0 1 2)
  (d<-iso-d "12-26") => (t/local-date 0 12 26)
  (d<-iso-d "2014-02") => (t/local-date 2014 2 1)
  (d<-iso-d "2014") => (t/local-date 2014 1 1)
  (d->iso-d (t/local-date 2014 1 2)) => "2014-01-02"
  (d->iso-d (t/local-date 0 1 2)) => "0000-01-02")

(facts
  "about RFC822 date parsing"
  (fact (dt<-rfc822-ts "Fri, 12 Jun 2015 17:50:00 UTC")
        => (t/date-time 2015 6 12 17 50 0 0)))


(facts
  "about relative date formatting"
  (fact (-> (t/now) (t/minus (t/seconds 10)) (dt->rel))
        => "few seconds ago")
  (fact (-> (t/now) (t/minus (t/minutes 10)) (dt->rel))
        => "10mi ago")
  (fact (-> (t/now) (t/plus (t/seconds 130)) (dt->rel))
        => "in 2mi")
  (fact (-> (t/now) (t/minus (t/hours 1)) (t/minus (t/minutes 10)) (dt->rel))
        => "1h 10mi ago")
  (fact (-> (t/now) (t/minus (t/days 1)) (t/minus (t/hours 1))
            (t/minus (t/minutes 10)) (dt->rel))
        => "1d 1h ago")
  (fact (-> (t/now) (t/minus (t/days 10)) (t/minus (t/hours 1))
            (t/minus (t/minutes 10)) (dt->rel))
        => "1w 3d ago")
  (fact (-> (t/now) (t/minus (t/days 14)) (t/minus (t/hours 1))
            (t/minus (t/minutes 10)) (dt->rel))
        => "2w ago")
  (fact (-> (t/now) (t/minus (t/days 40)) (t/minus (t/hours 1))
            (t/minus (t/minutes 10)) (dt->rel))
        => "1mo 1w ago")
  (fact (-> (t/now) (t/minus (t/days 70)) (t/minus (t/hours 1))
            (t/minus (t/minutes 10)) (dt->rel))
        => "2mo 1w ago")
  (fact (-> (t/now) (t/minus (t/days 400)) (t/minus (t/hours 1))
            (t/minus (t/minutes 10)) (dt->rel))
        => "1y 1mo ago")
  (fact (-> (t/now) (t/minus (t/days 800)) (t/minus (t/hours 1))
            (t/minus (t/minutes 10)) (dt->rel))
        => "2y 2mo ago"))
