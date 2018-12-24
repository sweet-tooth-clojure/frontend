(ns demo.routes
  (:require [re-frame.core :as rf]
            [accountant.core :as acc]
            [bide.core :as bide]
            
            [sweet-tooth.frontend.core.utils :as stcu]
            [sweet-tooth.frontend.routes.flow :as strf]
            [sweet-tooth.frontend.routes.utils :as stru]
            [sweet-tooth.frontend.form.flow :as stff]

            [demo.components.home :as h]))

(def routes
  [["/" :home]
   ["/init" :init]
   ["/topic" :topic]])

(def router
  (bide/router routes))

(defmethod strf/dispatch-route :home
  [{:keys [route-name params]} handler params]
  (rf/dispatch [::strf/load handler {:main [h/component]} params]))
