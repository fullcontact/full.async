# full.core

[![Clojars Project](http://clojars.org/fullcontact/full.core/latest-version.svg)](http://clojars.org/fullcontact/full.core)

Full.core is the core library for Clojure services at [FullContact](//fullcontact.com).

It contains the following:

* Config management
* Logging
* Various other small goodies

### Config management

With `full.core.config` you can manage yaml configurations for your app. It
has the following macros:

* `(defconfig var-name & path)` assigns @var-name to value in the path or raises RuntimeException if not found;
* `(defoptconfig var-name & path)` assigns @var-name to value in the path or nil
* `(defmappedconfig var-name f & path)` maps value in the path with f and assigns to @var-name

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

We can use config macros as follows:

```clojure
(defconfig app-name :app)   ; @app-name will be "facebookForCats"
(defconfig child :parent :child) ; @child will be "value"
(defmappedconfig hosts set :hosts)  ; @hosts will be #{"host1" "host2" "host3"}
(defoptconfig space-cakes :space-cakes) ; @space-cakes will be nil
(defconfig oh-no :this :will :raise)  ; will raise RuntimeException
```

Path to config file can be set via `-c path/to/file.yaml` or as `FULL_CONFIG`
env variable.
