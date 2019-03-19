(ns sweet-tooth.frontend.load-all-handler-ns
  "Require all namespaces with handlers, so that they're registered
  for registration. A little whacky but whatevs."
  (:require [sweet-tooth.frontend.core.flow]
            [sweet-tooth.frontend.filter.flow]
            [sweet-tooth.frontend.form.flow]
            [sweet-tooth.frontend.pagination.flow]
            [sweet-tooth.frontend.sync.flow]
            [sweet-tooth.frontend.nav.flow]
            [sweet-tooth.frontend.nav.ui.flow]))
