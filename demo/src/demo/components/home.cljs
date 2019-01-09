(ns demo.components.home
  (:require [re-frame.core :as rf]
            [sweet-tooth.frontend.form.components :as stfc]
            [sweet-tooth.frontend.form.flow :as stff]
            [sweet-tooth.frontend.sync.flow :as stsf]))

(defn component
  []
  (if (= :active @(rf/subscribe [::stsf/sync-state [:get :init]]))
    [:div "loading..."]
    [:div "Topic Thing"

     [:div [:h2 "Create new topic"]
      (let [form-path [:topic :create]
            {:keys [input form-state form-ui-state]} (stfc/form form-path)]
        [:form (stfc/on-submit form-path {:clear :all})
         [:div [input :text :topic/title {:placeholder "Title"}]]
         [:div [:input {:type :submit}]]])]

     [:div [:h2 "topics:"]
      (when (->> @(rf/subscribe [::stsf/sync-state-q [:create :topic]])
                 vals
                 (some #(= :active (:state %))))
        [:span "adding topic..."])

      [:div "topic count:" @(rf/subscribe [:topic-count])]
      (doall (map (fn [topic]
                    ^{:key (:db/id topic)}
                    [:div [:a {:href (str "/topic/" (:db/id topic))} (:topic/title topic)]])
                  @(rf/subscribe [:topics])))]]))
