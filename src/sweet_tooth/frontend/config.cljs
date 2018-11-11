(ns sweet-tooth.frontend.config
  (:require [sweet-tooth.frontend.sync.flow :as stsf]
            [sweet-tooth.frontend.sync.dispatch.ajax :as stsda]
            [sweet-tooth.frontend.sync.dispatch.bide :as stsdb]
            [sweet-tooth.frontend.routes.accountant :as stra]
            [sweet-tooth.frontend.routes.bide :as strb]
            [integrant.core :as ig]))


(def default-config
  {::stsf/sync  {:interceptors  []
                 :sync-dispatch (ig/ref ::stsda/sync)}
   ::stsda/sync {:req-adapter (ig/ref ::stsdb/req-adapter)}

   ::stra/accountant  {:match-route (ig/ref ::strb/match-route)}
   ::strb/match-route {:routes (ig/ref ::strb/routes)}
   ;; User must specify this
   ::strb/routes      []})

