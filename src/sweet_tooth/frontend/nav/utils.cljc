(ns sweet-tooth.frontend.nav.utils
  (:require [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.paths :as paths]
            [cemerick.url :as url]
            [clojure.string :as str]
            [clojur.walk :as walk]))

(defn query-params
  "Turn query string params into map with keyword keys"
  [path]
  (when (re-find #"\?" path)
    (-> (str/replace path #".*?\?" "")
        url/query->map
        walk/keywordize-keys)))

;; Does this belong in a pagination namespace?
(def page-param-keys
  #{:sort-by :sort-order :page :per-page})

(defn page-params
  "Extract just the page-related params from a map, converting page
  and per-page to integers"
  [params]
  (-> (select-keys params page-param-keys)
      (u/update-vals {[:page :per-page] #?(:cljs js/parseInt :clj #(Long. %))})))

(defn routed-entity
  [db entity-key param]
  (paths/get-path db :entity entity-key (paths/get-path db :nav :route :params param)))
