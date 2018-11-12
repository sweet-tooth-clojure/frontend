(ns sweet-tooth.frontend.paths
  "sweet tooth roots for the re-frame db"
  (:require [sweet-tooth.frontend.core.utils :as u]
            [integrant.core :as ig]))

(def config
  (atom {:form   :form
         :page   :page
         :entity :entity
         :nav    :nav}))

(def partial-path (comp vec rest))

(defn prefix
  [prefix-name]
  (get-in @config [:paths prefix-name]))

(defn full-path
  [prefix-name & partial-path]
  (apply u/flatv (prefix prefix-name) partial-path))

(defmethod ig/init-key ::paths
  [_ paths-config]
  (swap! config merge paths-config))
