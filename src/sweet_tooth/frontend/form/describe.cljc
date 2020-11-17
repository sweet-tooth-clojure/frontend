(ns sweet-tooth.frontend.form.describe
  (:require [re-frame.core :as rf]
            [medley.core :as medley]
            [sweet-tooth.describe :as d]
            [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.form.flow :as stff]
            [clojure.set :as set]))

;; The default form description subscription. Simply returns errors that have been
;; stored in the app db for the form.
(rf/reg-sub ::stored-errors
  (fn [[_ partial-form-path attr-path]]
    (if attr-path
      (rf/subscribe [::stff/attr-errors partial-form-path attr-path])
      (rf/subscribe [::stff/errors partial-form-path])))
  (fn [errors]
    {:errors errors}))

(defn errors-map
  [buffer rules]
  (->> (d/describe buffer rules)
       (d/map-rollup-descriptions)
       (medley/map-vals (fn [d] {:errors d}))))

(defn received-events?
  [input-events pred-events]
  (seq (set/intersection input-events pred-events)))

(defn reg-describe-validation-sub
  "Create a basic subscription that only shows errors when a submit is attempted"
  [sub-name rules & [show-errors-events]]
  (rf/reg-sub sub-name
    (fn [[_ partial-form-path]]
      (rf/subscribe [::stff/form partial-form-path]))
    (fn [{:keys [buffer input-events]} [_ _ attr-path]]
      (let [errors (errors-map buffer rules)]
        (if attr-path
          (when (received-events? (::stff/form input-events)
                                  (or show-errors-events #{:submit :attempt-submit}))
            (get-in errors (u/flatv attr-path)))
          {:prevent-submit? (seq errors)})))))
