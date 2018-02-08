(ns sweet-tooth.frontend.routes.flow
  (:require [re-frame.core :refer [reg-event-db reg-sub trim-v]]
            [sweet-tooth.frontend.routes.utils :as sfru]
            [sweet-tooth.frontend.paths :as paths]))

(reg-sub ::nav
  (fn [db _]
    (get db (paths/prefix :nav))))

(reg-sub ::routed-component
  :<- [::nav]
  (fn [nav _] (:component nav)))

(reg-sub ::params
  :<- [::nav]
  (fn [nav _] (:params nav)))

;; routed should have :params, :page-id, :component
;; TODO spec this
(reg-event-db ::load
  [trim-v]
  (fn [db [page-id component params]]
    (assoc db (paths/prefix :nav) {:component component
                                   :page-id page-id
                                   :params params
                                   :page-params (sfru/page-params params)})))
