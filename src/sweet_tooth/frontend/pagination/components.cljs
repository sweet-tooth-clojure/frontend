(ns sweet-tooth.frontend.pagination.components
  (:require [re-frame.core :refer [subscribe]]
            [secretary.core :as secretary]))

(defn page-nav
  [pager-id]
  (let [pager (subscribe [:pager pager-id])
        query-params (subscribe [:key :params :query-params])]
    (fn [pager-id]
      (let [{:keys [query result]} @pager
            url-base (aget js/document "location" "pathname")
            query-params @query-params]
        (into [:div.pager]
              (map (fn [page]
                     [:a.page-num
                      {:href (str url-base "?" (secretary/encode-query-params (assoc query-params :page page)))
                       :class (if (= (:page query) page) "active")}
                      page])
                   (map inc (range (:total-pages result)))))))))
