(ns sweet-tooth.frontend.paths
  "sweet tooth roots for the re-frame db"
  (:require [sweet-tooth.frontend.core.utils :as u]
            [integrant.core :as ig])
  (:refer-clojure :exclude [get-in update-in]))

(def config
  (atom {:form    :form
         :page    :page
         :entity  :entity
         :nav     :nav
         :failure :failure
         :system  :sweet-tooth/system}))

(def partial-path (comp vec rest))

(defn prefix
  [prefix-name]
  (get @config prefix-name))

(defn full-path
  [prefix-name & partial-path]
  (apply u/flatv (prefix prefix-name) partial-path))

(defn get-path
  [db prefix-name & partial-path]
  (clojure.core/get-in db (apply full-path prefix-name partial-path)))

(defmethod ig/init-key ::paths
  [_ paths-config]
  (swap! config merge paths-config))
