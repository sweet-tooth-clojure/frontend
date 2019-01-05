(ns sweet-tooth.frontend.routes.flow
  "deprecated"
  (:require [re-frame.core :as rf]
            [sweet-tooth.frontend.handlers :as sth]
            [sweet-tooth.frontend.nav.utils :as stnu]
            [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.paths :as paths]))

(rf/reg-sub ::nav
  (fn [db _]
    (get db (paths/prefix :nav))))

(rf/reg-sub ::routed-component
  :<- [::nav]
  (fn [nav [_ path]]
    (get-in (:components nav) (u/path path))))

(rf/reg-sub ::params
  :<- [::nav]
  (fn [nav _] (:params nav)))

;; routed should have :params, :page-id, :components
;; TODO spec this
;; TODO instead of page-id, route-id
(sth/rr rf/reg-event-db ::load
  [rf/trim-v]
  (fn [db [page-id components params]]
    (update db (paths/prefix :nav) (fn [nav]
                                     {:components  (merge (:components nav) components)
                                      :page-id     page-id
                                      :params      params
                                      :page-params (stnu/page-params params)}))))

(defmulti dispatch-route :route-name)
