(ns sweet-tooth.frontend.form.utils
  (:require [sweet-tooth.frontend.paths :as p]))

(defn update-in-form
  [db partial-path attr update-fn & args]
  (apply update-in db (into (p/full-path :form partial-path) [:data attr]) update-fn args))
