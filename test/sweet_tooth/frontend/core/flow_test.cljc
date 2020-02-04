(ns sweet-tooth.frontend.core.flow-test
  (:require [sweet-tooth.frontend.core.flow :as f]
            [sweet-tooth.frontend.paths :as paths]
            [sweet-tooth.frontend.test-config :as test-config]
            #?(:clj [clojure.test :as t :refer [is deftest testing]]
               :cljs [cljs.test :as t :include-macros true :refer [is deftest testing]])))

(deftest test-replace-ents
  (is (= (f/replace-ents {(paths/prefix :entity) {:topic {1 {:db/id            1
                                                             :topic/title      "topic"
                                                             :topic/post-count 1}}}}
                         {(paths/prefix :entity) {:topic {1 {:db/id       1
                                                             :topic/title "topic!"}}}})
         {(paths/prefix :entity) {:topic {1 {:db/id 1 :topic/title "topic!"}}}})))

(deftest test-update-db
  (testing "merges entities"
    (is (= (merge test-config/base-system
                  {:entity {:todo {1 {:db/id 1}}}})
           (f/update-db test-config/base-system
                        [[:entity {:todo {1 {:db/id 1}}}]])))

    (is (= (merge test-config/base-system
                  {:entity {:todo {1 {:db/id 1}
                                   2{:db/id 2}}}})
           (f/update-db test-config/base-system
                        [[:entity {:todo {1 {:db/id 1}}}]
                         [:entity {:todo {2 {:db/id 2}}}]])))))
