(ns sweet-tooth.frontend.core.flow-test
  (:require [sweet-tooth.frontend.core.flow :as f]
            [sweet-tooth.frontend.paths :as paths]
            #?(:clj [clojure.test :as t :refer [is deftest]]
               :cljs [cljs.test :as t :include-macros true :refer [is deftest]])))

(deftest replace-ents
  (is (= (f/replace-ents {paths/entity-prefix {:topic {1 {:db/id 1 :topic/title "topic" :topic/post-count 1}}}}
                         {paths/entity-prefix {:topic {1 {:db/id 1 :topic/title "topic!"}}}})
         {paths/entity-prefix {:topic {1 {:db/id 1 :topic/title "topic!"}}}})))
