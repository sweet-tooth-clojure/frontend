(ns sweet-tooth.frontend.core.compose
  "Tools for composing re-frame effects to produce a final effect map"
  (:require [re-frame.core :as rf]
            [clojure.spec.alpha :as s]
            [sweet-tooth.frontend.handlers :as sth]
            [taoensso.timbre :as log]))

(s/def ::dispatch
  (s/and vector? #(keyword? (first %))))

(s/def ::dispatch-n
  (s/coll-of ::dispatch))

(s/def ::ms int?)

(s/def ::dispatch-later-el
  (s/keys :req-un [::ms ::dispatch]))

(s/def ::dispatch-later
  (s/coll-of ::dispatch-later-el))

(s/def ::fx
  (s/keys :opt-un [::dispatch ::dispatch-n ::dispatch-later]))

(s/def ::dispatch-sugar-el
  (s/or :dispatch ::dispatch
        :dispatch-later-el ::dispatch-later-el))

(s/def ::dispatch-sugar
  (s/coll-of ::dispatch-sugar-el))

(s/def ::fx-sugar
  (s/or :dispatch-later-el ::dispatch-later-el
        :dispatch ::dispatch
        :effect ::fx
        :dispatch-sugar ::dispatch-sugar))

(defmulti merge-key (fn [x k y] k))

(defmethod merge-key :dispatch-n
  [x _ y]
  (into (or x []) y))

(defmethod merge-key :dispatch-later
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
        (= sugar-type :dispatch-sugar)    (mapv (comp first sugar-conformance->effectv) conformed)))

(defn effect-sugar->effectv
  [effect-sugar]
  (let [conformance (s/conform ::fx-sugar effect-sugar)]
    (if (= conformance ::s/invalid)
      (do (log/error "Effect not recognized for composition"
                     ::effect-not-recognized
                     {:effect effect-sugar})
          (throw (#?(:clj java.lang.AssertionError.
                     :cljs js/Error.) "Effect not recognized for composition")))
      (sugar-conformance->effectv conformance))))

(defn compose-fx
  "Vector of effects, where effects are expressed in more compact
  syntax. Composes all effects into a single effect using composition
  rules determined by effect key"
  [fx]
  (let [fx (if (s/valid? ::fx-sugar fx) [fx] fx)]
    (->> fx
         (map effect-sugar->effectv)
         (reduce into)
         (reduce merge-fx {}))))

(sth/rr rf/reg-event-fx ::compose-dispatch
  [rf/trim-v]
  (fn [cofx [fx]]
    (compose-fx fx)))
