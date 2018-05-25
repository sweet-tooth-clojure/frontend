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
  (fn [db [m]] (u/deep-merge db m)))

;; whereas deep merge will merge new entities with old, this replaces
;; old entities withnew
(core/rr reg-event-db ::replace-entities
  [trim-v]
  (fn [db [m]]
    (reduce-kv (fn [db entity-type entities]
                 (update-in db (paths/full-path :entity entity-type) merge entities))
               db
               (:entity m))))

(defn update-db
  "Takes a db and a vector of db-patches, and applies those patches to
  the db using the udpaters stored in 
  [:sweet-tooth.frontend/config :sweet-tooth.frontend.core.flow/update-db]
  of the app-db."
  [db [db-patches]]
  {:pre [(vector? db-patches)]}
  (let [updaters (vals (get-in db [:sweet-tooth.frontend/config :sweet-tooth.frontend.core.flow/update-db]))]
    (reduce (fn [db db-patch]
              (reduce (fn [db updater] (updater db db-patch))
                      db
                      updaters))
            db
            db-patches)))

(core/rr reg-event-db ::update-db [trim-v] update-db)

(defn db-patch-handle-entity
  [db db-patch]
  (let [entity-prefix (paths/prefix :entity)]
    (if-let [entity (get db-patch entity-prefix)]
      (update db entity-prefix u/deep-merge entity)
      db)))

(defn add-update-db-entity-config
  "Store config for update-db function in the re-frame app-db"
  [db]
  (assoc-in db [:sweet-tooth.frontend/config :sweet-tooth.frontend.core.flow/update-db (paths/prefix :entity)] db-patch-handle-entity))

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
;; TODO i don't like this atom :(
;; TODO dispose of debouncer
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
