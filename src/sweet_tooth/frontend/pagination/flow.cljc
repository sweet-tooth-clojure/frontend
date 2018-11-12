(ns sweet-tooth.frontend.pagination.flow
  (:require [re-frame.core :refer [reg-sub reg-event-db reg-event-fx trim-v]]
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
;; TODO namespace the page key
(defn db-patch-handle-page
  [db db-patch]
  (if-let [page (:page db-patch)]
    (-> (update db :page merge page)
        (assoc-in (paths/full-path :page :state (first (keys (:query page)))) :loaded))
    db))

;;---------
;; Subscriptions
;;---------

(defn pager
  "Retrieve a query and its results"
  [db query-id]
  (let [query (get-in db (paths/full-path :page :query query-id))]
    {:query query
     :result (get-in db (paths/full-path :page :result query))}))

(reg-sub ::pager (fn [db [_ query-id]] (pager db query-id)))

(reg-sub ::page-data
  (fn [db [_ query-id]]
    (let [{:keys [query result]} (pager db query-id)]
      (map #(get-in db (paths/full-path :entity (:type query) %))
           (:ordered-ids result)))))

(reg-sub ::page-result
  (fn [db [_ query-id]] (:result (pager db query-id))))

(reg-sub ::page-state
  (fn [db [_ query-id]]
    (get-in db (paths/full-path :page :state query-id))))

(reg-sub ::page-query
  (fn [db [_ query-id]] (:query (pager db query-id))))

;;---------
;; Helpers
;;---------

(defn update-db-page-loading
  "Use when initiating a GET request fetching paginataed data"
  [db {:keys [query-id] :as page-query}]
  (-> db
      (assoc-in (paths/full-path :page :query query-id) page-query)
      (assoc-in (paths/full-path :page :state query-id) :loading)))

(defn GET-page-fx
  [url page-defaults & [opts]]
  (fn [{:keys [db] :as cofx} [page-params]]
    (let [page-query (merge page-defaults page-params)]
      {:dispatch [::strf/http {:method GET
                               :url url
                               :params page-query
                               :on-success (get opts :on-success [::stcf/update-db])}]
       :db (update-db-page-loading db page-query)})))
