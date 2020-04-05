(ns sweet-tooth.frontend.form.utils
  (:require [sweet-tooth.frontend.paths :as p]))

(defn update-in-form
  "applies update on a form attr"
  [db partial-path attr update-fn & args]
  (apply update-in db (into (p/full-path :form partial-path) [:buffer attr]) update-fn args))
