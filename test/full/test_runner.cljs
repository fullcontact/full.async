(ns full.test-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [full.async-test]))

(doo-tests 'full.async-test)
