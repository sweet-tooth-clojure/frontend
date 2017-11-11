(ns sweet-tooth.frontend.filter.flow
  (:require [re-frame.core :refer [reg-event-db reg-event-fx trim-v reg-sub subscribe]]
            [clojure.string :as str]
            [sweet-tooth.frontend.form.flow :as stff]
            [taoensso.timbre :as timbre]))

;; TODO: min-length opt
(defn filter-query
  [query x-keys xs]
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

(defn filter-attr=
  [attr-val _ xs]
  (filter #(= attr-val %) xs))

(defn reg-filtered-sub
  [sub-name source-sub filter-form-path filter-fns]
  (reg-sub sub-name
    :<- [source-sub]
    :<- [::stff/data filter-form-path]
    (fn [[unfiltered form-data] _]
      ;; TODO some intelligence about unfiltered?
      (reduce (fn [filtered [form-attr filter-fn & filter-args]]
                (filter-fn (form-attr form-data) filter-args filtered))
              unfiltered
              filter-fns))))
