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
  (let [required (get match :required)]
    ;; TODO update this to be more specific
    (log/warn "reitit could not generate path. route might not exist, or might not have required params"
              {:route-name   name
               :route-params (select-keys route-params required)
               :required     required
               :match        (-> match
                                 (dissoc :data :required)
                                 (update :path-params select-keys required))})))

(defn on-no-route-default
  [path-or-name route-params query-params]
  (log/warn "reitit could not match route" {:route-args [path-or-name route-params query-params]}))

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
    [this name route-params query-params]
    (let [{{:keys [prefix]} :data :as match} (rc/match-by-name router name route-params)]
      (if (and match (not (:required match)))
        (cond-> match
          true                     (rc/match->path)
          (not-empty query-params) (str "?" (u/params-to-str query-params))
          prefix                   (as-> p (str prefix  p)))
        (when on-no-path
          (on-no-path name match route-params)
          nil))))

  (strp/req-id
    [this name]
    (strp/req-id this name {}))
  (strp/req-id
    [this name opts]
    (when (and (some? opts) (not (map? opts)))
      (log/error "req-id opts should be a map" {:opts opts}))
    (let [params (or (:route-params opts)
                     (:params opts)
                     opts)]
      (select-keys params (:required (rc/match-by-name router name)))))

  (strp/route
    [this path-or-name]
    (strp/route this path-or-name {} {}))
  (strp/route
    [this path-or-name route-params]
    (strp/route this path-or-name route-params {}))
  (strp/route
    [this path-or-name route-params query-params]
    (if-let [{:keys [data query-params] :as m} (if (keyword? path-or-name)
                                                 (rc/match-by-name router path-or-name route-params)
                                                 (reif/match-by-path router path-or-name))]
      (-> data
          (merge (dissoc m :data))
          (set/rename-keys {:name       :route-name
                            :parameters :params})
          (update :params (fn [{:keys [path query] :as _params}]
                            (merge path query query-params))))
      (when on-no-route
        (on-no-route path-or-name route-params query-params)
        nil))))

(defmethod strp/router :reitit
  [{:keys [routes router-opts] :as config}]
  (let [router (rc/router routes (merge {:compile coercion/compile-request-coercers}
                                        router-opts))]
    (map->ReititRouter (merge {:routes      routes
                               :router      router}
                              (select-keys config [:on-no-path :on-no-route])))))
