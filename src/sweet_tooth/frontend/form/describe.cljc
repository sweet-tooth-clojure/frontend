(ns sweet-tooth.frontend.form.describe
  (:require [re-frame.core :as rf]
            [sweet-tooth.frontend.form.flow :as stff]))

(rf/reg-sub ::stored-errors
  (fn [[_ partial-form-path attr-path]]
    (if attr-path
      (rf/subscribe [::stff/attr-errors partial-form-path attr-path])
      (rf/subscribe [::stff/errors partial-form-path])))
  (fn [errors]
    {:errors errors}))
