(ns sweet-tooth.cljs-test-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [sweet-tooth.frontend.core.compose-test]))

(doo-tests 'sweet-tooth.frontend.core.compose-test)
