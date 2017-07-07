(ns sweet-tooth.frontend.routes
  (:require [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.core.handlers :as stch]
            [re-frame.core :refer [dispatch]]))

;; Does this belong in a pagination namespace?
(def page-param-keys
  #{:sort-by :sort-order :page :per-page})

(defn page-params
  [params]
  (-> (select-keys params page-param-keys)
      (u/update-vals {[:page :per-page] #?(:cljs js/parseInt :clj #(Long. %))})))

;; TODO spec it ya dummy
(defn load
  [component params page-id]
  (dispatch [::stch/assoc-in [:nav] {:routed-component component
                                     :page-id page-id
                                     :params params
                                     :page-params (page-params params)}]))
