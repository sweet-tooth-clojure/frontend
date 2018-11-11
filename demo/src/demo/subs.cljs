(ns demo.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub :topic-count
  (fn [db _]
    (count (get-in db [:entity :topic]))))
