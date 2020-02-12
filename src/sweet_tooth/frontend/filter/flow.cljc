(ns sweet-tooth.frontend.filter.flow
  "Easy-ish tools for creating subscriptions that filter other
  subscriptions, typically used with form inputs"
  (:require [re-frame.core :as rf]
            [clojure.string :as str]
            [sweet-tooth.frontend.form.flow :as stff]))

;; TODO: min-length opt
(defn filter-contains-text
  "Filters `xs` returning those where any val (or vals corresponding to
  `x-keys`) contain `query`, case-insensitive.

  `val-transform` is applied to each val, defaulting to `str`. It can
  sometimes be useful, for example, to filter numbers as text rather
  than numbers. It can also be used to 'filter' vals by returning
  `nil`"
  [_
   query
   [{:keys [queried-keys mapping min-length]
     :or   {mapping    str
            min-length 0}}]
   xs]
  (if (<= (count query) min-length)
    xs
    (let [query (str/lower-case query)]
      (filter (fn [x]
                (not= -1
                      (.indexOf (->> (if (seq queried-keys)
                                       (select-keys x queried-keys)
                                       x)
                                     vals
                                     (map mapping)
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
  "Compares x val to form val"
  [form-attr attr-val [comp-fn key-fn] xs]
  (if (some? attr-val)
    (let [key-fn (or key-fn form-attr)]
      (filter #(let [x-val (key-fn %)]
                 (comp-fn x-val attr-val)) xs))
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

(defn filter-set
  [form-attr attr-val [key-fn] xs]
  (filter-attr-compare form-attr attr-val [#(contains? %2 %1) key-fn] xs))

(defn apply-filter-fns
  [unfiltered form-data filter-fns]
  (reduce (fn [unfiltered [form-attr filter-fn & filter-args]]
            (filter-fn form-attr (form-attr form-data) filter-args unfiltered))
          unfiltered
          filter-fns))

(defn reg-filtered-sub
  "Usage:
  (reg-filtered-sub
    :sub-name
    :source-sub-name
    [:form-name :method]
    [[:attr-1 filter-fn]
     [:attr-2 filter-fn]])

  `filter-fns` is a vector where each element is a vector"
  [sub-name source-sub filter-form-path filter-fns]
  (rf/reg-sub sub-name
    :<- [source-sub]
    :<- [::stff/buffer filter-form-path]
    (fn [[unfiltered form-data] _]
      (apply-filter-fns unfiltered form-data filter-fns))))
