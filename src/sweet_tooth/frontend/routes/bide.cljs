(ns sweet-tooth.frontend.routes.bide
  (:require [bide.core :as bide]
            [integrant.core :as ig]))

(defn match-route-fn
  [router param-coercion]
  (fn [path]
    (let [[route-name params query-params] (bide/match router path)]
      {:route-name route-name
       :params     (param-coercion route-name (merge params query-params))})))

(defmethod ig/init-key ::match-route
  [_ {:keys [router param-coercion]
      :or   {param-coercion identity}}]
  (match-route-fn router param-coercion))
