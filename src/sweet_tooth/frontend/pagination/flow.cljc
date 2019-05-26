(ns sweet-tooth.frontend.pagination.flow
  (:require [re-frame.core :as rf :refer [reg-sub reg-event-db reg-event-fx trim-v]]
            [cemerick.url :as url]
            [ajax.core :refer [GET]]
            [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.core.flow :as stcf]
            [sweet-tooth.frontend.form.flow :as stff]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [sweet-tooth.frontend.paths :as paths]))

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
  (get-in db (paths/full-path :page query-id)))

(reg-sub ::pager (fn [db [_ query-id]] (pager db query-id)))

(reg-sub ::page-data
  (fn [db [_ query-id]]
    (let [{:keys [query result]} (pager db query-id)]
      (map #(get-in db (paths/full-path :entity (:type query) %))
           (:ordered-ids (get result query))))))

(reg-sub ::page-result
  (fn [db [_ query-id]] (:result (pager db query-id))))

(reg-sub ::page-query
  (fn [db [_ query-id]] (:query (pager db query-id))))

(reg-sub ::page-count
  (fn [db [_ query-id]] (:page-count (pager db query-id))))

(reg-sub ::sync-state
  (fn [db [_ endpoint query-id]]
    (stsf/sync-state db [:get endpoint {:params (:query (pager db query-id))}])))

;;---------
;; Helpers
;;---------

(defn update-db-page-loading
  "Use when initiating a GET request fetching paginataed data"
  [db {:keys [query-id] :as page-query}]
  (assoc-in db (paths/full-path :page query-id :query) page-query))

(rf/reg-event-db ::update-db-page-loading
  [rf/trim-v]
  (fn [db [page-query]]
    (update-db-page-loading db page-query)))

(defn GET-page-fx
  [endpoint page-defaults & [opts]]
  (fn [{:keys [db] :as cofx} [page-params]]
    (let [page-query (merge page-defaults page-params)]
      {:dispatch-n [[::update-db-page-loading page-query]
                    [::stsf/sync [:get endpoint {:params     page-query
                                                 :on-success (get opts :on-success [::stcf/update-db])}]]]})))
