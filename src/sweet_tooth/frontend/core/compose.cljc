(ns sweet-tooth.frontend.core.compose
  (:require [re-frame.core :as rf]
            [sweet-tooth.frontend.handlers :as sth]
            [taoensso.timbre :as log]))

(defmulti merge-key (fn [x k y] k))

(defmethod merge-key :dispatch-n
  [x _ y]
  (into (or x []) y))

(defmethod merge-key :dispatch-later
  [x _ y]
  (into (or x []) y))

(defmethod merge-key :db-fns
  [x _ y]
  (into (or x []) y))

(defmethod merge-key :default
  [_ _ y]
  y)

(defn merge-fx
  [fx effect]
  (reduce-kv (fn [fx-acc effect-k effect-v]
               (update fx-acc effect-k merge-key effect-k effect-v))
             fx
             effect))

(defn compose-fx
  [fx]
  (->> fx
       (map (fn [effect]
              (cond (and (vector? effect) (every? vector? effect))
                    {:dispatch-n effect}

                    (vector? effect)
                    {:dispatch-n [effect]}

                    (map? effect)
                    effect

                    :else
                    (do (log/error "Effect not recognized for composition"
                                   ::effect-not-recognized
                                   {:effect effect})
                        (throw (js/Error. "Effect not recognized for composition"))))))
       (reduce merge-fx {})))

(sth/rr rf/reg-event-fx ::compose-dispatch
  [rf/trim-v]
  (fn [cofx [fx]]
    (compose-fx fx)))
