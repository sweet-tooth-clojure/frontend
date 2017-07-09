(ns sweet-tooth.frontend.core.flow
  (:require [re-frame.core :refer [reg-event-db dispatch trim-v path]]
            [sweet-tooth.frontend.core.utils :as u]))

(reg-event-db ::assoc-in
  [trim-v]
  (fn [db [path val]] (assoc-in db path val)))

(reg-event-db ::merge
  [trim-v]
  (fn [db [m & [path]]]
    (if path
      (update-in db path merge m)
      (merge db m))))

(defn replace-ents
  [db m]
  (update db :data (fn [data]
                     (reduce-kv (fn [data ent-type ents] (update data ent-type merge ents))
                                data
                                (:data m)))))

(reg-event-db ::deep-merge
  [trim-v]
  (fn [db [m]]
    (u/deep-merge db m)))

(reg-event-db ::toggle
  [trim-v]
  (fn [db [path]] (update-in db path not)))

(reg-event-db ::toggle-val
  [trim-v]
  (fn [db [path val]]
    (update-in db path #(if % nil val))))

;; Toggles set inclusion/exclusion from set


(reg-event-db ::set-toggle
  [trim-v]
  (fn [db [path val]]
    (update-in db path u/set-toggle val)))

(reg-event-db ::dissoc-in
  [trim-v]
  (fn [db [_ path]]
    (u/dissoc-in db path)))
