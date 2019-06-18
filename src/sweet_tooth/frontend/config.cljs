(ns sweet-tooth.frontend.config
  (:require [sweet-tooth.frontend.load-all-handler-ns]
            [sweet-tooth.frontend.paths :as paths]
            [sweet-tooth.frontend.handlers :as sth]
            [sweet-tooth.frontend.core.flow :as stcf]
            [sweet-tooth.frontend.pagination.flow :as stpf]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [sweet-tooth.frontend.sync.dispatch.ajax :as stsda]
            [sweet-tooth.frontend.form.flow :as stff]
            [sweet-tooth.frontend.nav.flow :as stnf]
            [sweet-tooth.frontend.routes :as stfr]
            
            [integrant.core :as ig]))


(def default-config
  {::sth/register-handlers []
   
   ::stsf/sync              {:sync-dispatch-fn (ig/ref ::stsda/sync-dispatch-fn)}
   ::stsda/sync-dispatch-fn {:router (ig/ref ::stfr/api-router)}

   ::stnf/handler {:dispatch-route-handler ::stnf/dispatch-route
                   :check-can-unload?      true
                   :router                 (ig/ref ::stfr/frontend-router)
                   :global-lifecycle       (ig/ref ::stnf/global-lifecycle)}

   ::stnf/global-lifecycle stnf/default-global-lifecycle

   ;;::stff/config {:data-id :db/id}
   ::stcf/update-db {(paths/prefix :entity) stcf/db-patch-handle-entity
                     :page                  stpf/db-patch-handle-page}})
