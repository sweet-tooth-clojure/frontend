(ns demo.components.show-topic
  (:require [re-frame.core :as rf]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [sweet-tooth.frontend.nav.flow :as stnf]))

(defn component
  []
  [:div
   [:div [:a {:href "/"} "home"]]
   (if (= :active @(rf/subscribe [::stnf/route-sync-state [:get :topic] :id]))
     [:div "loading..."]
     [:div
      [:h1 "Showing topic"]
      (:topic/title @(rf/subscribe [:routed-topic]))])])
