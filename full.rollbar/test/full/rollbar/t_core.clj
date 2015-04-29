(ns full.rollbar.t-core
  (:require [midje.sweet :refer :all]
            [full.rollbar.core :refer :all]))

(facts
  "about root-exception"
  (fact
    (let[root (Exception. "root")
         ex (Exception. "top" root)]
      (root-exception ex) => root)))

(facts
  "about error-msg"
  (fact
    (error-msg (Exception. "test")) => "test")
  (fact
    (error-msg (Exception.)) => "class java.lang.Exception"))

