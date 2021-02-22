(ns sweet-tooth.frontend.form.describe
  "Create subscription views for forms using the sweet-tooth.describe library.
  Very alpha."
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
  (fn [errors _]
    {:errors errors}))

(defn errors-map
  [buffer rules]
  (->> (d/describe buffer rules)
       (d/map-rollup-descriptions)
       (medley/map-vals (fn [d] {:errors d}))))

(defn received-events?
  [input-events pred-events]
  (seq (set/intersection input-events pred-events)))

(defn show-attr-on-blur
  [{:keys [input-events]} attr-path]
  (received-events? (get-in input-events (u/flatv attr-path))
                    #{:blur}))

(defn reg-describe-validation-sub
  "Create a basic subscription that only shows errors when a submit is attempted"
  [sub-name rules & [{:keys [submit-events show-attr]}]]
  (rf/reg-sub sub-name
    (fn [[_ partial-form-path]]
      (rf/subscribe [::stff/form partial-form-path]))
    (fn [{:keys [buffer input-events] :as form} [_ _ attr-path]]
      (let [errors            (errors-map buffer rules)
            submit-attempted? (received-events? (::stff/form input-events)
                                                (or submit-events #{:submit :attempt-submit}))
            show-attr         (or show-attr (constantly submit-attempted?))]
        (if attr-path
          ;; error messages for a specific attribute
          (when (show-attr form attr-path)
            (get-in errors (u/flatv attr-path)))
          ;; validation description for form as a whole
          (let [errors-seq (seq errors)]
            {:prevent-submit?   errors-seq
             :submit-prevented? (and errors-seq submit-attempted?)}))))))

(defn reg-combined-validation-subs
  "Given a seq of names of subs created with `reg-describe-validation-sub`,
  create a sub that deep merges all values."
  [sub-name validation-sub-names]
  (rf/reg-sub sub-name
    (fn [[_ partial-form-path attr-path]]
      (reduce (fn [signals sub]
                (conj signals (rf/subscribe (cond-> [sub partial-form-path]
                                              attr-path (conj attr-path)))))
              []
              validation-sub-names))
    (fn [signals]
      (apply medley/deep-merge signals))))
