(ns demo.components.home
  (:require [re-frame.core :as rf]
            [sweet-tooth.frontend.form.components :as stfc]
            [sweet-tooth.frontend.form.flow :as stff]
            [sweet-tooth.frontend.sync.flow :as stsf]))

(defn component
  []
  (if (= :active @(rf/subscribe [::stsf/sync-state [:get :init]]))
    [:div "birb"]
    [:div "Chirb"
     [:div [:h2 "Create new topic"]
      (let [form-path [:topic :create]
            {:keys [input form-state form-ui-state]} (stfc/form form-path)]
        [:form (stfc/on-submit form-path)
         [:div [input :text :topic/title {:placeholder "Title"}]]
         [:div [:input {:type :submit}]]])]
     [:div [:h2 "topics:"]
      [:div "topic count:" @(rf/subscribe [:topic-count])]
      (doall (map (fn [topic]
                    ^{:key (:db/id topic)}
                    [:div (:topic/title topic)])
                  @(rf/subscribe [:topics])))]]))
