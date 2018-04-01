(ns sweet-tooth.frontend.core.flow
  (:require [re-frame.core  :as rf :refer [reg-fx reg-event-db dispatch trim-v path]]
            [re-frame.loggers :refer [console]]
            [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.paths :as paths]
            [sweet-tooth.frontend.core :as core])
  (:import #?(:cljs [goog.async Debouncer])))

(core/rr reg-event-db ::assoc-in
  [trim-v]
  (fn [db [path val]] (assoc-in db path val)))

(core/rr reg-event-db ::merge
  [trim-v]
  (fn [db [m & [path]]]
    (if path
      (update-in db path merge m)
      (merge db m))))

(defn replace-ents
  [db m]
  (update db (paths/prefix :entity)
          (fn [data]
            (reduce-kv (fn [data ent-type ents] (update data ent-type merge ents))
                       data
                       ((paths/prefix :entity) m)))))

(core/rr reg-event-db ::deep-merge
  [trim-v]
  (fn [db [m]]
    (u/deep-merge db m)))

(core/rr reg-event-db ::toggle
  [trim-v]
  (fn [db [path]] (update-in db path not)))

(core/rr reg-event-db ::toggle-val
  [trim-v]
  (fn [db [path val]]
    (update-in db path #(if % nil val))))

;; Toggles set inclusion/exclusion from set
(core/rr reg-event-db ::set-toggle
  [trim-v]
  (fn [db [path val]]
    (update-in db path u/set-toggle val)))

(core/rr reg-event-db ::dissoc-in
  [trim-v]
  (fn [db [path]]
    (u/dissoc-in db path)))

;; debounce dispatches
(def debouncers (atom {}))

(defn new-debouncer
  [interval dispatch]
  #?(:cljs (doto (Debouncer. rf/dispatch interval)
             (.fire dispatch))))

(reg-fx ::debounce-dispatch
  (fn [value]
    (doseq [{:keys [ms id dispatch] :as effect} (remove nil? value)]
      (if (or (empty? dispatch) (not (number? ms)))
        (console :error "re-frame: ignoring bad :sweet-tooth.frontend.core.flow/debounce-dispatch value:" effect)
        (if-let [debouncer (get @debouncers id)]
          (.fire debouncer dispatch)
          (swap! debouncers assoc id (new-debouncer ms dispatch)))))))
