(ns sweet-tooth.frontend.core.flow
  (:require [re-frame.core  :as rf]
            [re-frame.db :as rfdb]
            [re-frame.loggers :as rfl]
            [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.paths :as paths]
            [sweet-tooth.frontend.handlers :as sth]
            [integrant.core :as ig])
  (:import #?(:cljs [goog.async Debouncer])))

(sth/rr rf/reg-event-db ::assoc-in
  [rf/trim-v]
  (fn [db [path val]] (assoc-in db path val)))

(sth/rr rf/reg-event-db ::merge
  [rf/trim-v]
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

(defn deep-merge
  [db [m]]
  (u/deep-merge db m))

(sth/rr rf/reg-event-db ::deep-merge
  [rf/trim-v]
  deep-merge)

(defn replace-entities
  "whereas deep merge will merge new entities with old, this replaces
  old entities with new."
  [db patches]
  (->> patches
       (filter #(= :entity (first %)))
       (map second)
       (reduce (fn [db patch]
                 (update-in db (paths/full-path :entity) (partial merge-with merge) patch))
               db)))

(sth/rr rf/reg-event-db ::replace-entities
  [rf/trim-v]
  (fn [db [patches]]
    (replace-entities db patches)))

(defn update-db
  "Takes a db and a vector of db-patches, and applies those patches to
  the db using the udpaters stored in
  [:sweet-tooth/system :sweet-tooth.frontend.core.flow/update-db]
  of the app-db."
  [db db-patches]
  {:pre [(vector? db-patches)]}
  (let [updaters (paths/get-path db :system ::update-db)]
    (reduce (fn [db [patch-key patch-val]]
              (if-let [updater (get updaters patch-key (get updaters :default))]
                (updater db patch-val)
                (throw (ex-info "no updater for patch" {:patch-key patch-key :patch-val patch-val :db-patches db-patches}))))
            db
            db-patches)))

(sth/rr rf/reg-event-db ::update-db
  [rf/trim-v]
  (fn [db [db-patches]]
    (update-db db db-patches)))

(defn db-patch-handle-entity
  [db db-patch]
  (let [entity-prefix (paths/prefix :entity)]
    (update db entity-prefix u/deep-merge db-patch)))

(sth/rr rf/reg-event-db ::toggle
  [rf/trim-v]
  (fn [db [path]] (update-in db path not)))

(sth/rr rf/reg-event-db ::toggle-val
  [rf/trim-v]
  (fn [db [path val]]
    (update-in db path #(if % nil val))))

;; Toggles set inclusion/exclusion from set
(sth/rr rf/reg-event-db ::set-toggle
  [rf/trim-v]
  (fn [db [path val]]
    (update-in db path u/set-toggle val)))

(sth/rr rf/reg-event-db ::dissoc-in
  [rf/trim-v]
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

(sth/rr rf/reg-fx ::debounce-dispatch
  (fn [value]
    (doseq [{:keys [ms id dispatch] :as effect} (remove nil? value)]
      (if (or (empty? dispatch) (not (number? ms)))
        (rfl/console :error "re-frame: ignoring bad :sweet-tooth.frontend.core.flow/debounce-dispatch value:" effect)
        (if-let [debouncer (get @debouncers id)]
          (.fire debouncer dispatch)
          (swap! debouncers assoc id (new-debouncer ms dispatch)))))))

(defmethod ig/init-key ::update-db
  [_ config]
  config)

;; system initialization
(rf/reg-event-fx ::init-system
  (fn [_ [_ config]]
    {::init-system config}))

(rf/reg-fx ::init-system
  (fn [config]
    (reset! rfdb/app-db {:sweet-tooth/system (-> config
                                                 ig/prep
                                                 ig/init)})))
