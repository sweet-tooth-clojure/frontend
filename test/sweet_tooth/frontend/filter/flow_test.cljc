(ns sweet-tooth.frontend.filter.flow-test
  (:require [sweet-tooth.frontend.filter.flow :as sut]
            #?(:clj [clojure.test :refer [is deftest testing]]
               :cljs [cljs.test :refer [is deftest testing] :include-macros true])))

(def ents
  [{:count 1}
   {:count 2}
   {:count 3}
   {:count 4}
   {:count 5
    :name  "bill"}])

(deftest test-filter-simple-numbers
  (is (= [{:count 5
           :name  "bill"}]
         (sut/apply-filter-fns ents
                               {:count 2
                                :name  "bill"}
                               [[:count sut/filter-attr>]
                                [:name sut/filter-attr=]]))))

(deftest test-filter-contains-text
  (testing "text"
    (is (= [{:count 5
             :name  "bill"}]
           (sut/apply-filter-fns ents
                                 {:query "bill"}
                                 [[:query sut/filter-contains-text]]))))

  (testing "numbers as strings"
    (is (= [{:count 1}]
           (sut/apply-filter-fns ents
                                 {:query "1"}
                                 [[:query sut/filter-contains-text]]))))

  (testing "minimum query length"
    (is (= ents
           (sut/apply-filter-fns ents
                                 {:query "1"}
                                 [[:query sut/filter-contains-text {:min-length 5}]]))))

  (testing "queried-keys"
    (is (empty? (sut/apply-filter-fns ents
                                      {:query "1"}
                                      [[:query sut/filter-contains-text {:queried-keys [:name]}]]))))

  (testing "mapping"
    (is (= [{:count 5
             :name  "bill"}]
           (sut/apply-filter-fns ents
                                 {:query "llib"}
                                 [[:query sut/filter-contains-text {:mapping (comp #(apply str %) reverse str)}]])))))


(deftest test-filter-set
  (is (= [{:count 1}]
         (sut/apply-filter-fns ents {:count #{1}} [[:count sut/filter-set]]))))
