(ns demo.components.home
  (:require [re-frame.core :as rf]))

(defn component
  []
  [:div "Chirb"
   [:div "topic count:" @(rf/subscribe [:topic-count])]])
