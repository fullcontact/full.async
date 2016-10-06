# full.async changelog


## 0.9.2

* `go-retry` accepts `on-error` argument

```clojure
(go-retry {:on-error (fn [exception]
                       (println "Exception raised:" (.getMessage exception)))
           :retries 3}
  (<? (async-operation)))
```

* `partition-all>>` is now deprecated.
