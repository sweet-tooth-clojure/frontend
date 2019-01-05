(ns sweet-tooth.frontend.config
  (:require [sweet-tooth.frontend.load-all-handler-ns]
            [sweet-tooth.frontend.paths :as paths]
            [sweet-tooth.frontend.handlers :as sth]
            [sweet-tooth.frontend.core.flow :as stcf]
            [sweet-tooth.frontend.pagination.flow :as stpf]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [sweet-tooth.frontend.sync.dispatch.ajax :as stsda]
            [sweet-tooth.frontend.sync.dispatch.bide :as stsdb]
            [sweet-tooth.frontend.nav.routes.bide :as strb]
            [sweet-tooth.frontend.form.flow :as stff]
            [sweet-tooth.frontend.nav.flow :as stnf]
            
            [integrant.core :as ig]))


(def default-config
  {::sth/register-handlers []
   
   ::stsf/sync              {:sync-dispatch-fn (ig/ref ::stsda/sync-dispatch-fn)}
   ::stsda/sync-dispatch-fn {:req-adapter (ig/ref ::stsdb/req-adapter)}

   ::stnf/handler {:check-can-unload? true
                   :match-route       (ig/ref ::strb/match-route)} 
   
   ;; User must specify :routes key
   ::strb/match-route {:routes nil}

   ;;::stff/config {:data-id :db/id}
   ::stcf/update-db {(paths/prefix :entity) stcf/db-patch-handle-entity
                     :page                  stpf/db-patch-handle-page}})
