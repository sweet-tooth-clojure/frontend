(ns sweet-tooth.frontend.routes.bide
  (:require [bide.core :as bide]
            [integrant.core :as ig]))

(defn match-route-fn
  [routes]
  (fn [path]
    (let [[route-name params query-params] (bide/match routes path)]
      {:route-name route-name
       :params     (merge params query-params)})))

(defmethod ig/init-key ::match-route
  [_ {:keys [routes]}]
  (match-route-fn routes))
