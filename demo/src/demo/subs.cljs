(ns demo.subs
  (:require [re-frame.core :as rf]
            [sweet-tooth.frontend.nav.utils :as stnu]))

(rf/reg-sub :topic-count
  (fn [db _]
    (count (get-in db [:entity :topic]))))

(rf/reg-sub :topics
  (fn [db _]
    (vals (get-in db [:entity :topic]))))

(rf/reg-sub :routed-topic
  (fn [db _]
    (stnu/routed-entity db :topic :id)))
