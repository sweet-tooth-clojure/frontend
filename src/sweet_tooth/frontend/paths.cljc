(ns sweet-tooth.frontend.paths
  "sweet tooth roots for the re-frame db"
  (:require [sweet-tooth.frontend.core.utils :as u]))

(def form-prefix :sweet-tooth.frontend.form)
(defn full-form-path
  [& partial-path]
  (apply u/flatv form-prefix partial-path))

(def partial-path (comp vec rest))

;; TODO namespace this
;; may require renaming keys from endpoint results
(def page-prefix :page)

(def entity-prefix :entity)

(def nav-prefix :nav)
