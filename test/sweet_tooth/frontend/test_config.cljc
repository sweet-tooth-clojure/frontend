(ns sweet-tooth.frontend.test-config
  (:require [sweet-tooth.frontend.core.flow :as stcf]
            [sweet-tooth.frontend.pagination.flow :as stpf]
            [sweet-tooth.frontend.sync.flow :as stsf]))

(def db-updaters
  {:entity    stcf/db-patch-handle-entity
   :exception stsf/db-patch-handle-exception
   :page      stpf/db-patch-handle-page
   :default   merge})

(def base-system
  {:sweet-tooth/system {::stcf/update-db db-updaters}})
