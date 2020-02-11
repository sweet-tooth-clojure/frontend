(ns sweet-tooth.frontend.filter.flow-test
  (:require [sweet-tooth.frontend.filter.flow :as sut]
            #?(:clj [clojure.test :refer [is deftest]]
               :cljs [cljs.test :refer [is deftest] :include-macros true])))

(deftest filter-fns
  (is (= [{:count 5
           :name  "bob"}]
         (sut/apply-filter-fns [{:count 1}
                                {:count 2}
                                {:count 3}
                                {:count 4}
                                {:count 5
                                 :name  "bob"}]
                               {:count 2
                                :name  "bob"}
                               [[:count sut/filter-attr>]
                                [:name sut/filter-attr=]]))))
