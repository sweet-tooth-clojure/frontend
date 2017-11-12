(ns sweet-tooth.frontend.filter.flow
  (:require [re-frame.core :refer [reg-event-db reg-event-fx trim-v reg-sub subscribe]]
            [clojure.string :as str]
            [sweet-tooth.frontend.form.flow :as stff]
            [taoensso.timbre :as timbre]))

;; TODO: min-length opt
(defn filter-query
  [_ query x-keys xs]
  (if (empty? query)
    xs
    (let [query (str/lower-case query)]
      (filter (fn [x]
                (not= -1
                      (.indexOf (->> (if (seq x-keys)
                                       (select-keys x x-keys)
                                       x)
                                     vals
                                     (filter string?)
                                     (str/join " ")
                                     (str/lower-case))
                                query)))
              xs))))

(defn filter-toggle
  "Restrict xs to vals whose `form-attr` is truthy, if `toggle?` is true"
  [form-attr apply-toggle? _ xs]
  (if apply-toggle?
    (filter form-attr xs)
    xs))

(defn filter-attr-compare
  "Compares x val to form val. x must have some val for the given key"
  [form-attr attr-val [comp-fn key-fn] xs]
  (if attr-val
    (let [key-fn (or key-fn form-attr)]
      (filter #(let [x-val (key-fn %)]
                 (and x-val
                      (comp-fn x-val attr-val))) xs))
    xs))

(defn filter-attr=
  [form-attr attr-val [key-fn] xs]
  (filter-attr-compare form-attr attr-val [= key-fn] xs))

(defn filter-attr>
  [form-attr attr-val [key-fn] xs]
  (filter-attr-compare form-attr attr-val [> key-fn] xs))

(defn filter-attr<
  [form-attr attr-val [key-fn] xs]
  (filter-attr-compare form-attr attr-val [< key-fn] xs))

(defn filter-attr>=
  [form-attr attr-val [key-fn] xs]
  (filter-attr-compare form-attr attr-val [>= key-fn] xs))

(defn filter-attr<=
  [form-attr attr-val [key-fn] xs]
  (filter-attr-compare form-attr attr-val [<= key-fn] xs))

(defn reg-filtered-sub
  [sub-name source-sub filter-form-path filter-fns]
  (reg-sub sub-name
    :<- [source-sub]
    :<- [::stff/data filter-form-path]
    (fn [[unfiltered form-data] _]
      ;; TODO some intelligence about unfiltered?
      (reduce (fn [filtered [form-attr filter-fn & filter-args]]
                (filter-fn form-attr (form-attr form-data) filter-args filtered))
              unfiltered
              filter-fns))))
