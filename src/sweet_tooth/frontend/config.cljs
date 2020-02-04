(ns sweet-tooth.frontend.config
  (:require [sweet-tooth.frontend.load-all-handler-ns]
            [sweet-tooth.frontend.handlers :as sth]
            [sweet-tooth.frontend.core.flow :as stcf]
            [sweet-tooth.frontend.pagination.flow :as stpf]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [sweet-tooth.frontend.sync.dispatch.ajax :as stsda]
            [sweet-tooth.frontend.nav.flow :as stnf]
            [sweet-tooth.frontend.routes :as stfr]
            [sweet-tooth.frontend.routes.reitit :as strr]

            [integrant.core :as ig]))

(def default-config
  {::sth/register-handlers {}

   ::stsf/sync {:router           (ig/ref ::stfr/sync-router)
                :sync-dispatch-fn (ig/ref ::stsda/sync-dispatch-fn)}

   ::stsda/sync-dispatch-fn {}

   ::stnf/handler {:dispatch-route-handler ::stnf/dispatch-route
                   :check-can-unload?      true
                   :router                 (ig/ref ::stfr/frontend-router)
                   :global-lifecycle       (ig/ref ::stnf/global-lifecycle)}

   ::stfr/frontend-router strr/config-defaults
   ::stfr/sync-router     strr/config-defaults

   ::stnf/global-lifecycle stnf/default-global-lifecycle

   ;;::stff/config {:data-id :db/id}
   ::stcf/update-db {:entity    stcf/db-patch-handle-entity
                     :exception stsf/db-patch-handle-exception
                     :page      stpf/db-patch-handle-page
                     :default   merge}})
