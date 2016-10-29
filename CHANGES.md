# full.async changelog

## 1.0.0

* ClojureScript support
* `debounce>>`
* `go-retry` now supports `should-retry-fn` parameter. `error-fn` param is 
deprecated
* Removed `try<?` and `try<??`

## 0.9.2

* `go-retry` now supports `on-error` parameter

```clojure
(go-retry {:on-error (fn [exception]
                       (println "Exception raised:" (.getMessage exception)))
           :retries 3}
  (<? (async-operation)))
```
