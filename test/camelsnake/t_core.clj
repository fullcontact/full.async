(ns camelsnake.t_core
  (:require [midje.sweet :refer :all]
            [camelsnake.core :refer :all]))

;; Snake input

(facts "about snake->snake"
       (->snake_case "snake_case") => "snake_case"
       (->snake_case "oneword") => "oneword"
       (->snake_case "a_snake_case") => "a_snake_case"
       (->snake_case "f_o_o") => "f_o_o"
       (->snake_case "x_http_request") => "x_http_request"
       (->snake_case "foo_x") => "foo_x")

(facts "about snake->camel"
       (->camelCase "snake_case") => "snakeCase"
       (->camelCase "oneword") => "oneword"
       (->camelCase "a_snake_case") => "aSnakeCase"
       (->camelCase "f_o_o") => "fOO"
       (->camelCase "x_http_request") => "xHttpRequest"
       (->camelCase "foo_x") => "fooX")

(facts "about snake-kebab"
       (->kebab-case "snake_case") => "snake-case"
       (->kebab-case "oneword") => "oneword"
       (->kebab-case "a_snake_case") => "a-snake-case"
       (->kebab-case "f_o_o") => "f-o-o"
       (->kebab-case "x_http_request") => "x-http-request"
       (->kebab-case "foo_x") => "foo-x")

;; Camel input

(facts "about camel->camel"
       (->camelCase "snakeCase") => "snakeCase"
       (->camelCase "oneword") => "oneword"
       (->camelCase "aSnakeCase") => "aSnakeCase"
       (->camelCase "ASnakeCase") => "aSnakeCase"
       (->camelCase "fOO") => "fOo"
       (->camelCase "xHttpRequest") => "xHttpRequest"
       (->camelCase "fooX") => "fooX")

(facts "about camel->snake"
       (->snake_case "snakeCase") => "snake_case"
       (->snake_case "oneword") => "oneword"
       (->snake_case "aSnakeCase") => "a_snake_case"
       (->snake_case "ASnakeCase") => "a_snake_case"
       (->snake_case "FOO") => "foo"
       (->snake_case "FOo") => "f_oo"
       (->snake_case "xHttpRequest") => "x_http_request"
       (->snake_case "fooX") => "foo_x")

(facts "about camel->kebab"
       (->kebab-case "snakeCase") => "snake-case"
       (->kebab-case "oneword") => "oneword"
       (->kebab-case "aSnakeCase") => "a-snake-case"
       (->kebab-case "ASnakeCase") => "a-snake-case"
       (->kebab-case "FOO") => "foo"
       (->kebab-case "foO") => "fo-o"
       (->kebab-case "HTTPRequest") => "http-request")

;; Kebab input

(facts "about kebab->kebab"
       (->kebab-case "snake-case") => "snake-case"
       (->kebab-case "oneword") => "oneword"
       (->kebab-case "a-snake-case") => "a-snake-case"
       (->kebab-case "foo") => "foo"
       (->kebab-case "fo-o") => "fo-o"
       (->kebab-case "http-request") => "http-request")

(facts "about kebab->snake"
       (->snake_case "snake-case") => "snake_case"
       (->snake_case "oneword") => "oneword"
       (->snake_case "a-snake-case") => "a_snake_case"
       (->snake_case "f-o-o") => "f_o_o"
       (->snake_case "x-http-request") => "x_http_request"
       (->snake_case "foo-x") => "foo_x")

(facts "about kebab->camel"
       (->camelCase "snake-case") => "snakeCase"
       (->camelCase "oneword") => "oneword"
       (->camelCase "a-snake-case") => "aSnakeCase"
       (->camelCase "f-oo") => "fOo"
       (->camelCase "x-http-request") => "xHttpRequest"
       (->camelCase "foo-x") => "fooX")


(facts
  "about case conversions"
  (->camelCase-keys {:old-index 5 :new-index 7 :very-old-index 1}) => {"oldIndex" 5 "newIndex" 7 "veryOldIndex" 1}
  (->camelCase-keys {:oneword 3}) => {"oneword" 3}
  (->keyword "FooBarBaz") => :foo-bar-baz
  (->keyword "fooBarBaz") => :foo-bar-baz
  (->keyword "foobarbaz") => :foobarbaz
  (->keyword "FooBARBaz") => :foo-bar-baz
  (->keyword "FOOBARBAZ") => :foobarbaz
  (->keyword "XFullContactAccountId") => :x-full-contact-account-id
  (->keyword "iPod") => :i-pod)