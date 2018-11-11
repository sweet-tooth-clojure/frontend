(ns sweet-tooth.frontend.routes.flow
  (:require [re-frame.core :refer [reg-event-db reg-sub trim-v]]
            [sweet-tooth.frontend.core :as stc]
            [sweet-tooth.frontend.routes.utils :as sfru]
            [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.paths :as paths]))

(reg-sub ::nav
  (fn [db _]
    (get db (paths/prefix :nav))))

(reg-sub ::routed-component
  :<- [::nav]
  (fn [nav [_ path]]
    (get-in (:components nav) (u/path path))))

(reg-sub ::params
  :<- [::nav]
  (fn [nav _] (:params nav)))

;; routed should have :params, :page-id, :components
;; TODO spec this
(stc/rr reg-event-db ::load
  [trim-v]
  (fn [db [page-id components params]]
    (update db (paths/prefix :nav) (fn [nav]
                                     {:components  (merge (:components nav) components)
                                      :page-id     page-id
                                      :params      params
                                      :page-params (sfru/page-params params)}))))

(defmulti dispatch-route :route-name)
