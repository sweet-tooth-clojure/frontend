(ns sweet-tooth.frontend.routes.accountant
  (:require [accountant.core :as acc]
            [integrant.core :as ig]
            [sweet-tooth.frontend.routes.flow :as strf]))

(defonce accountant-configured? (atom false))

(defmethod ig/init-key ::accountant
  [_ {:keys [match-route]}]
  (acc/configure-navigation!
    {:nav-handler (comp strf/dispatch-route match-route)
     :path-exists? (comp boolean match-route)}))
