(ns sweet-tooth.frontend.sync.dispatch.bide
  (:require [bide.core :as bide]
            [integrant.core :as ig]))

(defmethod ig/init-key ::req-adapter
  [_ {:keys [routes route-param-fn query-param-fn]
      :or   {route-param-fn (constantly nil)
             query-param-fn (constantly nil)}}]
  (let [router (bide/router routes)]
    (fn [[method res {:keys [params] :as opts}]]
      [method res (assoc opts :uri (bide/resolve router
                                                 res
                                                 (route-param-fn method res params)
                                                 (query-param-fn method res params)))])))
