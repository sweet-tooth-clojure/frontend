(ns sweet-tooth.frontend.pagination.components
  (:require [re-frame.core :refer [subscribe]]
            [sweet-tooth.frontend.pagination.flow :as stpf]
            [sweet-tooth.frontend.nav.flow :as stnf]
            [sweet-tooth.frontend.routes :as stfr]))

(defn page-nav
  "A component that displays a link to each page. Current page has the
  `active` class"
  [pager-id]
  (let [pager (subscribe [::stpf/pager pager-id])
        route (subscribe [::stnf/route])]
    (fn [pager-id]
      (let [{:keys [query page-count]}                              @pager
            {:keys [path-params query-params route-name] :as route} @route]
        (into [:div.pager]
              (map (fn [page]
                     [:a.page-num
                      {:href  (stfr/path route-name path-params (assoc query-params :page page))
                       :class (if (= (:page query) page) "active")}
                      page])
                   (map inc (range page-count))))))))
