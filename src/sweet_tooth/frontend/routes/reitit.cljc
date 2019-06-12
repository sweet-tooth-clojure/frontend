(ns sweet-tooth.frontend.routes.reitit
  (:require [reitit.core :as rc]
            [reitit.frontend :as reif]
            [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.routes.protocol :as strp]
            [clojure.set :as set]))

(defrecord ReititRouter [routes router on-no-match]
  strp/Router
  (strp/path
    [this name route-params]
    (strp/path this name route-params {}))
  (strp/path
    [{:keys [router]} name route-params query-params]
    (or (cond-> (rc/match-by-name router name route-params)
          true                     (rc/match->path)
          (not-empty query-params) (str "?" (u/map->params query-params)))
        (when on-no-match
          (on-no-match name route-params)
          nil)))

  (strp/route
    [this path]
    (if-let [{:keys [data]} (reif/match-by-path router path)]
      (cond-> (set/rename-keys data {:name       :route-name
                                     :parameters :params})
        (= (keys (:parameters data)) [:path :query]) (update :params (fn [{:keys [path query]}] (merge path query))))
      (when on-no-match
        (on-no-match path)
        nil))))

(defmethod strp/router :reitit
  [{:keys [routes on-no-match] :as config}]
  (let [router (rc/router routes)]
    (map->ReititRouter {:routes      routes
                        :router      router
                        :on-no-match on-no-match})))
