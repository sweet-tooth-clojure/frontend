(ns demo.location-dispatch
  (:require [re-frame.core :as rf]
            [accountant.core :as acc]
            [clojure.string :as str]
            [bide.core :as bide]

            [demo.routes :as routes]
            [demo.components.home :as h]
            
            [sweet-tooth.frontend.core.utils :as stcu]
            [sweet-tooth.frontend.routes.flow :as strf]
            [sweet-tooth.frontend.routes.utils :as stru]
            [sweet-tooth.frontend.form.flow :as stff]))


(defmulti dispatch-route (fn [handler params] handler))

(defmethod dispatch-route :home
  [handler params]
  (rf/dispatch [::strf/load handler {:main [h/component]} params]))

(defonce nav
  ;; defonce to prevent this from getting re-configured with
  ;; boot/reload on every change
  ;;
  ;; TODO add this to ST frontend
  (acc/configure-navigation!
    {:nav-handler
     (fn [path]
       (let [[res params query-string] (bide/match routes/routes path)]
         (dispatch-route res (merge params query-string))))
     :path-exists?
     (fn [path]
       (boolean (bide/match routes/routes path)))}))
