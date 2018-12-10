(ns sweet-tooth.frontend.routes.accountant
  (:require [accountant.core :as acc]
            [integrant.core :as ig]
            [sweet-tooth.frontend.routes.flow :as strf]))

(defonce accountant-configured? (atom false))

;; TODO use unconfigure-navigation! with halt-key
(defmethod ig/init-key ::accountant
  [_ {:keys [match-route]}]
  (when-not @accountant-configured?
    (reset! accountant-configured? true)
    (acc/configure-navigation!
      {:nav-handler  (comp strf/dispatch-route match-route)
       :path-exists? (comp :route-name match-route)})))
