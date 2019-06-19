(ns demo.core
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [re-frame.db :as rfdb]
            [sweet-tooth.frontend.core.utils :as stcu]
            [sweet-tooth.frontend.core.flow :as stcf]
            [sweet-tooth.frontend.nav.routes.bide :as strb]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [sweet-tooth.frontend.sync.dispatch.bide :as stsdb]
            [sweet-tooth.frontend.config :as stconfig]
            [sweet-tooth.frontend.nav.flow :as stnf]
            [goog.events]
            [integrant.core :as ig]

            [demo.subs]
            [demo.handlers]
            [demo.sync.dispatch.local :as dsdl]
            [demo.routes :as routes])
  (:import [goog.events EventType]))

(enable-console-print!)

(extend-protocol ISeqable
  js/NodeList
  (-seq [node-list] (array-seq node-list))

  js/HTMLCollection
  (-seq [node-list] (array-seq node-list)))

(defn app
  []
  [:div.container.app "App!"
   @(rf/subscribe [::stnf/routed-component :main])])

(def system-config
  (merge stconfig/default-config
         {::stsf/sync         {:sync-dispatch-fn (ig/ref ::dsdl/sync)}
          ::stsdb/req-adapter {:routes routes/sync-routes}

          ::dsdl/sync {:delay 1000}

          ::strb/match-route {:routes         routes/browser-routes
                              :param-coercion routes/browser-route-coercion}}))

(defn -main []
  (rf/dispatch-sync [::stcf/init-system system-config])
  (rf/dispatch-sync [:init])
  (r/render [app] (stcu/el-by-id "app")))

(defonce initial-load (delay (-main)))
@initial-load

(defn stop [_]
  (when-let [system (:sweet-tooth/system @rfdb/app-db)]
    (ig/halt! system)))
