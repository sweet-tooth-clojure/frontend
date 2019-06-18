(ns sweet-tooth.frontend.routes.reitit
  (:require [reitit.core :as rc]
            [reitit.frontend :as reif]
            [reitit.coercion :as coercion]
            [taoensso.timbre :as log]
            [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.routes.protocol :as strp]
            [clojure.set :as set]))

(defn on-no-path-default
  [name match route-params]
  (log/warn "reitit could not generate path" name match route-params))

(defn on-no-route-default
  [path]
  (log/warn "reitit could not match route" path))

(def config-defaults
  {:use         :reitit
   :on-no-path  on-no-path-default
   :on-no-route on-no-route-default})

(defrecord ReititRouter [routes router on-no-path on-no-route]
  strp/Router
  (strp/path
    [this name]
    (strp/path this name {} {}))
  (strp/path
    [this name route-params]
    (strp/path this name route-params {}))
  (strp/path
    [{:keys [router]} name route-params query-params]
    (let [{{:keys [prefix]} :data :as match} (rc/match-by-name router name route-params)]
      (if-not (:required match)
        (cond-> match
          true                     (rc/match->path)
          (not-empty query-params) (str "?" (u/params-to-str query-params))
          prefix                   (as-> p (str prefix  p)))
        (when on-no-path
          (on-no-path name match route-params)
          nil))))

  (strp/match
    [this name]
    (strp/match this name {}))
  (strp/match
    [this name route-params]
    (strp/match this name route-params))
  
  (strp/route
    [this path]
    (if-let [{:keys [data query-params] :as m} (reif/match-by-path router path)]
      (-> data
          (merge (dissoc m :data))
          (set/rename-keys {:name       :route-name
                            :parameters :params})
          (update :params (fn [{:keys [path query] :as params}]
                            (merge path query query-params))))
      (when on-no-route
        (on-no-route path)
        nil))))

(defmethod strp/router :reitit
  [{:keys [routes] :as config}]
  (let [router (rc/router routes {:compile coercion/compile-request-coercers})]
    (map->ReititRouter (merge {:routes      routes
                               :router      router}
                              (select-keys config [:on-no-path :on-no-route])))))
