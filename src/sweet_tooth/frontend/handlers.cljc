(ns sweet-tooth.frontend.handlers
  (:require [re-frame.core :refer [reg-event-db dispatch trim-v path]]))

(reg-event-db :assoc-in
  [trim-v]
  (fn [db [path val]] (assoc-in db path val)))

(reg-event-db :merge
  [trim-v]
  (fn [db [m & [path]]]
    (if path
      (update-in db path merge m)
      (merge db m))))

(defn deep-merge-with
  "Like merge-with, but merges maps recursively, applying the given fn
  only when there's a non-map at a particular level.
  (deepmerge + {:a {:b {:c 1 :d {:x 1 :y 2}} :e 3} :f 4}
               {:a {:b {:c 2 :d {:z 9} :z 3} :e 100}})
  -> {:a {:b {:z 3, :c 3, :d {:z 9, :x 1, :y 2}}, :e 103}, :f 4}"
  [f & maps]
  (apply
    (fn m [& maps]
      (if (every? map? maps)
        (apply merge-with m maps)
        (apply f maps)))
    maps))

(defn deep-merge
  "Like merge, but merges maps recursively"
  [db m]
  (deep-merge-with (fn [_ x] x) db m))

(defn replace-ents
  [db m]
  (update db :data (fn [data]
                     (reduce-kv (fn [data ent-type ents] (update data ent-type merge ents))
                                data
                                (:data m)))))


(reg-event-db :deep-merge
  [trim-v]
  (fn [db [m]]
    (deep-merge db m)))


(reg-event-db :toggle
  [trim-v]
  (fn [db [path]] (update-in db path not)))

(reg-event-db :toggle-val
  [trim-v]
  (fn [db [path val]]
    (update-in db path #(if % nil val))))

;; Toggles set inclusion/exclusion from set
(reg-event-db :set-toggle
  [trim-v]
  (fn [db [path val]]
    (update-in db path (fn [xs]
                         (let [xs (or xs #{})]
                           ((if (contains? xs val) disj conj) xs val))))))

(defn dissoc-in
  "Remove value in `m` at `p`"
  [m p]
  (update-in m (butlast p) dissoc (last p)))

(reg-event-db :dissoc-in
  [trim-v]
  (fn [db [path]]
    (update-in db (butlast path) dissoc (last path))))
