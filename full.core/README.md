# full.core

[![Clojars Project](http://clojars.org/fullcontact/full.core/latest-version.svg)](http://clojars.org/fullcontact/full.core)

Full.core is the core library for Clojure services at [FullContact](//fullcontact.com).

It contains the following:

* Config management
* Logging
* Various other small goodies

### Config management

With `full.core.config` you can manage yaml configurations for your app.

If you have the following config:

```yaml
app: facebookForCats
hosts:
  - host1
  - host2
  - host3
parent:
  child: value
```

We can use config as follows:

```clojure
(def app-name (opt :app))   ; @app-name will be "facebookForCats"
(def child (opt [:parent :child])) ; @child will be "value"
(def hosts (opt :hosts :mapper set))  ; @hosts will be #{"host1" "host2" "host3"}
(def space-cakes (opt :space-cakes :default nil)) ; @space-cakes will be nil
(def oh-no (opt [:this :will :raise]))  ; @oh-no will raise RuntimeException
```

Path to config file can be set via `-c path/to/file.yaml` or as `FULL_CONFIG`
env variable.
