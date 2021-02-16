(ns sweet-tooth.frontend.failure.flow
  "experimental handlers for temporarily showing failures"
  (:require [re-frame.core :as rf]
            [sweet-tooth.frontend.handlers :as sth]
            [sweet-tooth.frontend.paths :as p]))

(sth/rr rf/reg-event-fx ::add-failure
  [rf/trim-v]
  (fn [{:keys [db]} [failure]]
    (let [new-db (update-in db (p/full-path :failure) (fnil conj []) {:failure  failure
                                                                      :ui-state :show})
          pos    (dec (count (p/get-path new-db :failure)))]
      {:db             new-db
       :dispatch-later [{:ms 2000 :dispatch [::hide-failure pos]}]})))

(sth/rr rf/reg-event-db ::hide-failure
  [rf/trim-v]
  (fn [db [pos]]
    (assoc-in db (p/full-path :failure pos :ui-state) :hide)))

(rf/reg-sub ::failures
  (fn [db]
    (p/get-path db :failure)))

(rf/reg-sub ::visible-failures
  :<- [::failures]
  (fn [failures]
    (filter #(= :show (:ui-state %)) failures)))
