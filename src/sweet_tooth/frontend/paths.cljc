(ns sweet-tooth.frontend.paths
  "sweet tooth roots for the re-frame db"
  (:require [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.core :as stc]))

(def partial-path (comp vec rest))

(defn prefix
  [prefix-name]
  (get-in @stc/config [:paths prefix-name]))

(defn full-path
  [prefix-name & partial-path]
  (apply u/flatv (prefix prefix-name)  partial-path))
