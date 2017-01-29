(ns sweet-tooth.frontend.pagination.handlers
  (:require [re-frame.core :refer [reg-event-db trim-v]]
            [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.form.handlers :as stfh]))

(defn merge-page
  [db [page-data]]
  (reduce (fn [db x]
            (cond-> (u/deep-merge db x)
              (:page x) (assoc-in [:page :state (first (keys (:query (:page x))))] :loaded)))
          db
          page-data))

(reg-event-db :merge-page [trim-v] merge-page)

#_(def submit-form-success-page
  (stfh/success-base merge-page))

#_(reg-event-db ::clear-on-success-page
  [trim-v]
  (fn [db args]
    (-> (submit-form-success-page db args)
        (clear args))))
