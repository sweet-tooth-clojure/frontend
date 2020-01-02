(ns sweet-tooth.frontend.pagination.components
  (:require [re-frame.core :refer [subscribe]]
            [sweet-tooth.frontend.pagination.flow :as stpf]
            [sweet-tooth.frontend.nav.flow :as stnf]
            [sweet-tooth.frontend.routes :as stfr]))

(defn window
  [page-count current-page window-size]
  (let [n      (Math/floor (/ (dec window-size) 2))
        l      (- current-page n)
        r      (+ current-page n)
        l-diff (when (< l 1) (- 1 l))
        r-diff (when (> r page-count) (- r page-count))

        [l r] (cond->> [(- current-page n) (+ current-page n)]
                l-diff (map #(+ % l-diff))
                r-diff (map #(- % r-diff)))]
    (vec (range l (inc r)))))

(defn page-subset
  [page-count current-page window-size]
  (let [central-range (window page-count current-page window-size)]
    (cond->> central-range
      (> (first central-range) 2)              (into [nil])
      (not= (first central-range) 1)           (into [1])
      (< (last central-range)(dec page-count)) (#(into % [nil]))
      (not= (last central-range) page-count)   (#(into % [page-count])))))

(defn page-nav
  "A component that displays a link to each page. Current page has the
  `active` class"
  [pager-id & [window-size]]
  (let [{:keys [query page-count]}                    @(subscribe [::stpf/pager pager-id])
        {:keys [path-params query-params route-name]} @(subscribe [::stnf/route])
        current-page                                  (:page query)
        page-nums                                     (if (and window-size (< window-size page-count))
                                                        (page-subset page-count current-page window-size)
                                                        (range 1 (inc page-count)))]

    (->> page-nums
         (map (fn [page]
                (if page
                  [:a.page-num
                   {:href  (stfr/path route-name path-params (assoc query-params :page page))
                    :class (when (= (:page query) page) "active")}
                   page]
                  [:span.page-space [:i.fal.fa-ellipsis-h]])))
         (into [:div.pager]))))
