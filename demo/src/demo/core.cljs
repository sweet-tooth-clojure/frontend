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
   @(rf/subscribe [::strf/routed-component :main])])

(defn -main []
  (rf/dispatch-sync [:init (-> stconfig/default-config
                               (merge {::stsf/sync         {:sync-dispatch-fn (ig/ref ::dsdl/sync)}
                                       ::stsdb/req-adapter {:routes routes/routes}

                                       ::dsdl/sync {:delay 500}

                                       ::strb/match-route {:router routes/router}})
                               ig/prep
                               ig/init)])
  (r/render [app] (stcu/el-by-id "app"))
  (goog.events/listen js/window
                      EventType.CLICK
                      (fn [] (rf/dispatch-sync [:window-clicked]))))

(-main)
