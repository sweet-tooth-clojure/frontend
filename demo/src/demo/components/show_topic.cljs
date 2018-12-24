(ns demo.components.show-topic
  (:require [re-frame.core :as rf]))

(defn component
  []
  [:div [:h1 "Showing topic"]
   (:topic/title @(rf/subscribe [:routed-topic]))])
