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
   ["/init" :init]])

(defn match-route
  [path]
  (let [[route-name params query-params] (bide/match routes path)]
    {:route-name route-name
     :params     (merge params query-params)}))


(defmethod strf/dispatch-route :home
  [{:keys [route-name params]} handler params]
  (rf/dispatch [::strf/load handler {:main [h/component]} params]))
