(ns demo.routes
  (:require [re-frame.core :as rf]
            [accountant.core :as acc]
            [bide.core :as bide]
            
            [sweet-tooth.frontend.core.utils :as stcu]
            [sweet-tooth.frontend.nav.utils :as stru]
            [sweet-tooth.frontend.form.flow :as stff]
            [sweet-tooth.frontend.nav.flow :as stnf]

            [demo.components.home :as h]
            [demo.components.show-topic :as st]))

(def sync-routes
  [["/init" :init]
   ["/topic/:id" :topic]])

(def browser-routes
  [["/" :home]
   ["/topic/:id" :topic]])

(defn browser-route-coercion
  [_ params]
  (stcu/update-vals params {[:id] js/parseInt}))

(defmethod stnf/route-lifecycle :home
  [route]
  {:components {:main [h/component]}})

(defmethod stnf/route-lifecycle :topic
  [route]
  {:enter      (fn [] (rf/dispatch [:load-topic (:params route)]))
   ;; :can-exit?  (fn [] (js/confirm "You sure?"))
   :components {:main [st/component]}})
