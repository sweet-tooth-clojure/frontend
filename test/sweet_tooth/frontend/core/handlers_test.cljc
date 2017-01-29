(ns sweet-tooth.frontend.core.handlers-test
  (:require [sweet-tooth.frontend.handlers :as h]
            #?(:clj [clojure.test :as t :refer [is deftest]]
               :cljs [cljs.test :as t :include-macros true :refer [is deftest]])))

(deftest replace-ents
  (is (= (h/replace-ents {:data {:topic {1 {:db/id 1 :topic/title "topic" :topic/post-count 1}}}}
                         {:data {:topic {1 {:db/id 1 :topic/title "topic!"}}}})
         {:data {:topic {1 {:db/id 1 :topic/title "topic!"}}}})))
