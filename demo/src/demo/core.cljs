(ns demo.core
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [sweet-tooth.frontend.core.utils :as stcu]
            [sweet-tooth.frontend.core.flow :as stcf]
            [sweet-tooth.frontend.routes.flow :as strf]
            [sweet-tooth.frontend.routes.bide :as strb]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [sweet-tooth.frontend.sync.dispatch.bide :as stsdb]
            [sweet-tooth.frontend.config :as stconfig]
            [sweet-tooth.frontend.nav.handler :as stnh]
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
   @(rf/subscribe [::stnh/routed-component :main])])

(def system-config
  (merge stconfig/default-config
         {::stsf/sync         {:sync-dispatch-fn (ig/ref ::dsdl/sync)}
          ::stsdb/req-adapter {:routes routes/api-routes}

          ::dsdl/sync {:delay 500}

          ::strb/match-route {:routes         routes/browser-routes
                              :param-coercion routes/browser-route-coercion}}))

(defn -main []
  (rf/dispatch-sync [::stcf/init-system system-config])
  (rf/dispatch-sync [:init])
  (r/render [app] (stcu/el-by-id "app"))
  #_(goog.events/listen js/window
                        EventType.CLICK
                        (fn [] (rf/dispatch-sync [:window-clicked]))))

(-main)
