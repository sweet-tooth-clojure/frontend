(ns demo.routes
  (:require [re-frame.core :as rf]
            [accountant.core :as acc]
            [bide.core :as bide]
            
            [sweet-tooth.frontend.core.utils :as stcu]
            [sweet-tooth.frontend.routes.flow :as strf]
            [sweet-tooth.frontend.routes.utils :as stru]
            [sweet-tooth.frontend.form.flow :as stff]

            [demo.components.home :as h]
            [demo.components.show-topic :as st]))

(def api-routes
  [["/init" :init]
   ["/topic/:id" :topic]])

(def browser-routes
  [["/" :home]
   ["/topic/:id" :topic]])

(def browser-router
  (bide/router browser-routes))

(defn browser-route-coercion
  [_ params]
  (stcu/update-vals params {[:id] js/parseInt}))

(defmethod strf/dispatch-route :home
  [{:keys [route-name params] :as opts}]
  (rf/dispatch [::strf/load route-name {:main [h/component]} params]))

(defmethod strf/dispatch-route :topic
  [{:keys [route-name params] :as opts}]
  (rf/dispatch [:load-topic params])
  (rf/dispatch [::strf/load route-name {:main [st/component]} params]))
