(ns sweet-tooth.frontend.routes.utils
  (:require [sweet-tooth.frontend.core.utils :as u]
            [cemerick.url :as url]
            [clojure.string :as str]
            [sweet-tooth.frontend.paths :as paths]))

(defn query-params
  "Turn query string params into map with keyword keys"
  [path]
  (if (re-find #"\?" path)
    (-> (str/replace path #".*?\?" "")
        url/query->map
        clojure.walk/keywordize-keys)))

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
  (get-in db (paths/full-path :entity
                              entity-key
                              (get-in db (paths/full-path :nav :params param)))))
