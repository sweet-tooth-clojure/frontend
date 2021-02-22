(ns sweet-tooth.frontend.nav.utils
  (:require [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.form.flow :as stff]
            [sweet-tooth.frontend.paths :as paths]
            [cemerick.url :as url]
            [clojure.string :as str]
            [clojure.walk :as walk]))

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
  "Returns an entity by looking up its entity-key in nav params"
  [db entity-key param-key]
  (paths/get-path db :entity entity-key (paths/get-path db :nav :route :params param-key)))

(defn routed-entity-form
  [db partial-form-path param-key]
  (paths/get-path db :form partial-form-path (select-keys (paths/get-path db :nav :route :params) [param-key])))

(defn initialize-form-with-routed-entity
  [db form-path entity-key param-key & [form-opts]]
  (let [ent (routed-entity db entity-key param-key)]
    (stff/initialize-form db [(conj form-path (select-keys ent [param-key])) (merge {:buffer ent} form-opts)])))

(defn route
  [db]
  (paths/get-path db :nav :route))

(defn params
  [db]
  (-> db route :params))

(defn route-name
  [db]
  (-> db route :route-name))
