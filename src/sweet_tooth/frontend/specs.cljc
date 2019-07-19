(ns sweet-tooth.frontend.specs
  (:require [clojure.spec.alpha :as s]))

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
