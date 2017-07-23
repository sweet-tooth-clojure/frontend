(ns sweet-tooth.frontend.pagination.flow
  (:require [re-frame.core :refer [reg-sub reg-event-db trim-v]]
            [cemerick.url :as url]
            [ajax.core :refer [GET]]
            [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.core.flow :as stcf]
            [sweet-tooth.frontend.form.flow :as stff]
            [sweet-tooth.frontend.paths :as paths]
            [sweet-tooth.frontend.remote.flow :as strf]))

;;---------
;; Handlers
;;---------

;; TODO spec possible page states and page keys
;; TODO this is just deep merging across all maps, plus updating page state
;; TODO namespace the page key
(defn merge-page
  [db [page-data]]
  (reduce (fn [db x]
            (cond-> (u/deep-merge db x)
              (:page x) (assoc-in [paths/page-prefix :state (first (keys (:query (:page x))))] :loaded)))
          db
          page-data))

(reg-event-db ::merge-page [trim-v] merge-page)

(def submit-form-success-page
  (stff/success-base merge-page))

(reg-event-db ::submit-form-success-page
  [trim-v]
  (fn [db args]
    (submit-form-success-page db args)))

;;---------
;; Subscriptions
;;---------

(defn pager
  "Retrieve a query and its results"
  [db query-id]
  (let [query (get-in db [paths/page-prefix :query query-id])]
    {:query query
     :result (get-in db [paths/page-prefix :result query])}))

(reg-sub ::pager (fn [db [_ query-id]] (pager db query-id)))

(reg-sub ::page-data
  (fn [db [_ query-id]]
    (let [{:keys [query result]} (pager db query-id)]
      (map #(get-in db [paths/entity-prefix (:type query) %])
           (:ordered-ids result)))))

(reg-sub ::page-result
  (fn [db [_ query-id]] (:result (pager db query-id))))

(reg-sub ::page-state
  (fn [db [_ query-id]]
    (get-in db [paths/page-prefix :state query-id])))

(reg-sub ::page-query
  (fn [db [_ query-id]] (:query (pager db query-id))))

;;---------
;; Helpers
;;---------

(defn update-db-page-loading
  "Use when initiating a GET request fetching paginataed data"
  [db {:keys [query-id] :as page-query}]
  (-> db
      (assoc-in [paths/page-prefix :query query-id] page-query)
      (assoc-in [paths/page-prefix :state query-id] :loading)))

(defn GET-page-fx
  [url page-defaults]
  (fn [{:keys [db] :as cofx} args]
    (let [[page-params] args
          page-query (merge page-defaults page-params)]
      {::strf/http {:method GET
                    :url url
                    :params page-query
                    :on-success [::merge-page]}
       :db (update-db-page-loading db page-query)})))
