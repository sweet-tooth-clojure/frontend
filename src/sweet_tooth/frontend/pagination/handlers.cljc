(ns sweet-tooth.frontend.pagination.handlers
  (:require [re-frame.core :refer [reg-event-db trim-v]]
            [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.form.handlers :as stfh]))

;; TODO spec possible page states

;; TODO this is just deep merging across all maps, plus updating page state

;; TODO namespace the page key
(defn merge-page
  [db [page-data]]
  (reduce (fn [db x]
            (cond-> (u/deep-merge db x)
              (:page x) (assoc-in [:page :state (first (keys (:query (:page x))))] :loaded)))
          db
          page-data))

(reg-event-db ::merge-page [trim-v] merge-page)

(def submit-form-success-page
  (stfh/success-base merge-page))

(reg-event-db ::submit-form-success-page
  [trim-v]
  (fn [db args]
    (submit-form-success-page db args)))
