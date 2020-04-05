(ns sweet-tooth.frontend.handlers-test
  (:require [sweet-tooth.frontend.handlers :as sut]
            [re-frame.core :as rf]
            #?(:clj [clojure.test :refer [is deftest testing use-fixtures]]
               :cljs [cljs.test :refer [is deftest testing use-fixtures] :include-macros true])))

(use-fixtures :each (fn [f] (reset! sut/handlers {}) (f)))

(deftest test-rr
  (testing "stores a registration in the handlers atom"
    (is (= {'sweet-tooth.frontend.handlers-test [{:reg-fn       rf/reg-event-db
                                                  :id           ::hi
                                                  :interceptors [inc]
                                                  :handler-fn   +}]}
           (sut/rr rf/reg-event-db ::hi [inc] +)))))

(deftest test-register-handler*
  (testing "adds interceptors to specific handler"
    (is (= [rf/reg-event-db
            ::hi
            [inc dec]
            +]
           (sut/register-handler*
            {:reg-fn       rf/reg-event-db
             :id           ::hi
             :interceptors [inc]
             :handler-fn   +}
            'sweet-tooth.frontend.handlers-test
            {::hi [dec]}))))

  (testing "adds interceptors to handler whose id is in ns"
    (is (= [rf/reg-event-db
            ::hi
            [inc dec]
            +]
           (sut/register-handler*
            {:reg-fn       rf/reg-event-db
             :id           ::hi
             :interceptors [inc]
             :handler-fn   +}
            'sweet-tooth.frontend.handlers-test
            {'sweet-tooth.frontend.handlers-test [dec]})))))
