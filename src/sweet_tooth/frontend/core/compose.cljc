(ns sweet-tooth.frontend.core.compose
  "Tools for composing re-frame effects to produce a final effect map"
  (:require [re-frame.core :as rf]
            [clojure.spec.alpha :as s]
            [sweet-tooth.frontend.handlers :as sth]
            [sweet-tooth.frontend.specs :as sfs]
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

(defn sugar-conformance->effectv
  [[sugar-type conformed]]
  (cond (= sugar-type :effect)            [conformed]
        (= sugar-type :dispatch)          [{:dispatch-n [conformed]}]
        (= sugar-type :dispatch-later-el) [{:dispatch-later [conformed]}]
        (= sugar-type :dispatch-sugar)    (mapv sugar-conformance->effectv conformed)))

(defn effect-sugar->effectv
  [effect-sugar]
  (let [conformance (s/conform ::sfs/fx-sugar effect-sugar)]
    (if (= conformance ::s/invalid)
      (do (log/error "Effect not recognized for composition"
                     ::effect-not-recognized
                     {:effect effect-sugar})
          (throw (js/Error. "Effect not recognized for composition")))
      (sugar-conformance->effectv conformance))))

(defn compose-fx
  "Vector of effects, where effects are expressed in more compact
  syntax. Composes all effects into a single effect using composition
  rules determined by effect key"
  [fx]
  (->> fx
       (map effect-sugar->effectv)
       (reduce into)
       (reduce merge-fx {})))

(sth/rr rf/reg-event-fx ::compose-dispatch
  [rf/trim-v]
  (fn [cofx [fx]]
    (compose-fx fx)))
