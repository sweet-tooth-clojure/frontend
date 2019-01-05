(ns demo.components.show-topic
  (:require [re-frame.core :as rf]))

(defn component
  []
  [:div
   [:div [:a {:href "/"} "home"]]
   [:h1 "Showing topic"]
   (:topic/title @(rf/subscribe [:routed-topic]))])
