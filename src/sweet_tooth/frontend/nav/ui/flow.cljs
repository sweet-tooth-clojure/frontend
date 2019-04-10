(ns sweet-tooth.frontend.nav.ui.flow
  "Add ui-controlling state should be cleared on navigation changes"
  (:require [re-frame.core :as rf]
            [sweet-tooth.frontend.handlers :as sth]
            [sweet-tooth.frontend.paths :as paths]))

(rf/reg-sub ::ui
  (fn [db [_ & path]]
    (get-in db (paths/full-path :nav :ui path))))

(defn assoc-in-ui
  [db path val]
  (assoc-in db (paths/full-path :nav :ui path) val))

(sth/rr rf/reg-event-db ::assoc-in-ui
  [rf/trim-v]
  (fn [db [path val]]
    (assoc-in-ui db path val)))

(sth/rr rf/reg-event-db ::clear
  []
  (fn [db [_ route-or-params]]
    (assoc-in-ui db route-or-params nil)))
