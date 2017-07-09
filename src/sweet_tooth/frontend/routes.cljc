(ns sweet-tooth.frontend.routes
  (:require [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.core.handlers :as stch]
            [re-frame.core :refer [dispatch]]
            [cemerick.url :as url]
            [clojure.string :as str]))

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

;; TODO spec it ya dummy
(defn load
  "Updates the db state with a component and metadata to reflect the
  current route"
  [component params page-id]
  (dispatch [::stch/assoc-in [:nav] {:routed-component component
                                     :page-id page-id
                                     :params params
                                     :page-params (page-params params)}]))
