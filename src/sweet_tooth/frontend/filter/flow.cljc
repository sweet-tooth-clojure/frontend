(ns sweet-tooth.frontend.filter.flow
  (:require [re-frame.core :as rf]
            [clojure.string :as str]
            [sweet-tooth.frontend.form.flow :as stff]))

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

(defn reg-filtered-sub
  [sub-name source-sub filter-form-path filter-fns]
  (rf/reg-sub sub-name
    :<- [source-sub]
    :<- [::stff/buffer filter-form-path]
    (fn [[unfiltered form-data] _]
      ;; TODO some intelligence about unfiltered?
      (reduce (fn [filtered [form-attr filter-fn & filter-args]]
                (filter-fn form-attr (form-attr form-data) filter-args filtered))
              unfiltered
              filter-fns))))
