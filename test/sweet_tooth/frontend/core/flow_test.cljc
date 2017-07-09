(ns sweet-tooth.frontend.core.flow-test
  (:require [sweet-tooth.frontend.flow :as f]
            #?(:clj [clojure.test :as t :refer [is deftest]]
               :cljs [cljs.test :as t :include-macros true :refer [is deftest]])))

(deftest replace-ents
  (is (= (f/replace-ents {:data {:topic {1 {:db/id 1 :topic/title "topic" :topic/post-count 1}}}}
                         {:data {:topic {1 {:db/id 1 :topic/title "topic!"}}}})
         {:data {:topic {1 {:db/id 1 :topic/title "topic!"}}}})))
