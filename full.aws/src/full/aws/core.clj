(ns full.aws.core
  (:require [full.core.config :refer [defconfig]])
  (:import (com.amazonaws.regions Region Regions)))

(defconfig region-name :aws :region)

(def region (delay (Region/getRegion (Regions/fromName @region-name))))