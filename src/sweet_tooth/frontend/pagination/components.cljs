(ns sweet-tooth.frontend.pagination.components
  (:require [re-frame.core :refer [subscribe]]
            [cemerick.url :as url]
            [sweet-tooth.frontend.pagination.flow :as stpf]
            [sweet-tooth.frontend.nav.flow :as stnf]))

(defn page-nav
  "A component that displays a link to each page. Current page has the
  `active` class"
  [pager-id]
  (let [pager        (subscribe [::stpf/pager pager-id])
        query-params (subscribe [::stnf/params pager-id])]
    (fn [pager-id]
      (let [{:keys [query page-count]} @pager
            url-base                   (aget js/document "location" "pathname")
            query-params               @query-params]
        (into [:div.pager]
              (map (fn [page]
                     [:a.page-num
                      {:href  (str url-base "?" (url/map->query (assoc query-params :page page)))
                       :class (if (= (:page query) page) "active")}
                      page])
                   (map inc (range page-count))))))))
