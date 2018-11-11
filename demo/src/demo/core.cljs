(ns demo.core
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            ;; ["react-transition-group/TransitionGroup" :as TransitionGroup]
            ;; ["react-transition-group/Transition" :as Transition]
            ;; ["react-transition-group/CSSTransition" :as CSSTransition]
            [sweet-tooth.frontend.form.flow :as stff] ;; prep handlers for registration
            [sweet-tooth.frontend.core :as st-core]
            [sweet-tooth.frontend.core.utils :as stcu]
            [sweet-tooth.frontend.core.flow :as stcf]
            [sweet-tooth.frontend.routes.flow :as strf]
            [sweet-tooth.frontend.form.components :as stfc]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [sweet-tooth.frontend.sync.dispatch.ajax :as stsda]
            [goog.events]
            [integrant.core :as ig]
            [bide.core :as bide]

            [demo.handlers]
            [demo.sync.dispatch.local :as dsdl]
            [demo.routes :as routes]
            [demo.location-dispatch :as ld])
  (:import [goog.events EventType]))

(st-core/register-handlers)
(enable-console-print!)
(def config
  {::stsf/sync {:interceptors []
                :sync-dispatch (ig/ref ::dsdl/sync)}
   ::stsda/sync {:req-adapter (fn [[method res opts]]
                                [method res (assoc opts :uri (bide/resolve routes/routes res))])}

   ::dsdl/sync {}})

(extend-protocol ISeqable
  js/NodeList
  (-seq [node-list] (array-seq node-list))

  js/HTMLCollection
  (-seq [node-list] (array-seq node-list)))

(defn app
  []
  [:div.container.app "App!"
   @(rf/subscribe [::strf/routed-component])])

(defn -main []
  (rf/dispatch-sync [:init (-> config ig/prep ig/init)])
  (r/render [app] (stcu/el-by-id "app"))
  (goog.events/listen js/window
                      EventType.CLICK
                      (fn [] (rf/dispatch-sync [:window-clicked]))))

(-main)
