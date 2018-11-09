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
            [goog.events]
            [integrant.core :as ig]

            [demo.handlers])
  (:import [goog.events EventType]))

(st-core/register-handlers)
(enable-console-print!)
(def config
  {::stsf/sync {:interceptors []
                :sync-dispatch (fn [req] (println "SYNC DISPATCH!" req))
                ;; :dispatch     #ig/ref :sweet-tooth.frontend.dispatch.mock/flow
                }
   ;; :sweet-tooth.frontend.dispatch.mock/flow {}
   })

(extend-protocol ISeqable
  js/NodeList
  (-seq [node-list] (array-seq node-list))

  js/HTMLCollection
  (-seq [node-list] (array-seq node-list)))

(defn app
  []
  [:div.container.app "App!"])

(defn -main []
  (rf/dispatch-sync [:init (ig/init config)])
  (r/render [app] (stcu/el-by-id "app"))
  (goog.events/listen js/window
                      EventType.CLICK
                      (fn [] (rf/dispatch-sync [:window-clicked]))))

(-main)
