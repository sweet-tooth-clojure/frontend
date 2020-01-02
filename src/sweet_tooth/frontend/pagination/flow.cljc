(ns sweet-tooth.frontend.pagination.flow
  (:require [re-frame.core :as rf]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [sweet-tooth.frontend.paths :as paths]))

;;---------
;; Handlers
;;---------

;; TODO spec possible page states and page keys
;; TODO namespace the page key
(defn db-patch-handle-page
  [db db-patch]
  (-> db
      (update :page merge db-patch)
      (assoc-in (paths/full-path :page :state (first (keys (:query db-patch)))) :loaded)))

;;---------
;; Subscriptions
;;---------

(defn pager
  "Retrieve a query and its results"
  [db query-id]
  (paths/get-path db :page query-id))

(rf/reg-sub ::pager (fn [db [_ query-id]] (pager db query-id)))

(rf/reg-sub ::page-data
  (fn [db [_ query-id]]
    (let [{:keys [query result]} (pager db query-id)]
      (map #(paths/get-path db :entity (:type query) %)
           (:ordered-ids (get result query))))))

(rf/reg-sub ::page-result
  (fn [db [_ query-id]] (:result (pager db query-id))))

(rf/reg-sub ::page-query
  (fn [db [_ query-id]] (:query (pager db query-id))))

(rf/reg-sub ::page-count
  (fn [db [_ query-id]] (:page-count (pager db query-id))))

(rf/reg-sub ::sync-state
  (fn [db [_ endpoint query-id]]
    (stsf/sync-state db [:get endpoint {::stsf/req-id query-id}])))

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
  (fn [_cofx [page-params]]
    (let [page-query (merge page-defaults page-params)]
      {:dispatch-n [[::update-db-page-loading page-query]
                    [::stsf/sync [:get endpoint {:query-params page-query
                                                 :on           {:success (get-in opts [:on :success] [::stsf/update-db :$ctx])}
                                                 ::stsf/req-id (:query-id page-query)}]]]})))
