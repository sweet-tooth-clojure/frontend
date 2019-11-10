(ns sweet-tooth.frontend.core.compose-test
  (:require [sweet-tooth.frontend.core.compose :as stcc]
            #?(:clj [clojure.test :as t :refer [is deftest]]
               :cljs [cljs.test :as t :include-macros true :refer [is deftest]])))

(deftest composes-nothing
  (is (= {}
         (stcc/compose-fx []))))

(deftest composes-dispatch-n
  (is (= {:dispatch-n [[:x]]}
         (stcc/compose-fx [[:x]])))

  (is (= {:dispatch-n [[:x] [:y]]}
         (stcc/compose-fx [[:x] [:y]]))))

(deftest composes-dispatch-later
  (is (= {:dispatch-later [{:ms 1000, :dispatch [:x]}
                           {:ms 1000, :dispatch [:y]}]}
         (stcc/compose-fx [{:ms 1000 :dispatch [:x]}
                           {:ms 1000 :dispatch [:y]}]))))

(deftest composes-mix
  (is (= {:dispatch-n     [[:a] [:b]]
          :dispatch-later [{:ms 1000, :dispatch [:x]}
                           {:ms 1000, :dispatch [:y]}]}
         (stcc/compose-fx [[:a]
                           [:b]
                           {:ms 1000 :dispatch [:x]}
                           {:ms 1000 :dispatch [:y]}]))))

(deftest composes-fx
  (is (= {:dispatch [:x]
          :dispatch-n [[:y] [:z]]}
         (stcc/compose-fx [{:dispatch [:x]}
                           {:dispatch-n [[:y]]}
                           {:dispatch-n [[:z]]}])))

  (is (= {:dispatch [:x]
          :dispatch-n [[:a] [:y] [:z]]}
         (stcc/compose-fx [{:dispatch [:x]}
                           [:a]
                           {:dispatch-n [[:y]]}
                           {:dispatch-n [[:z]]}])))


  (is (= {:dispatch-later [{:ms 1000, :dispatch [:x]}
                           {:ms 1000, :dispatch [:y]}]}
         (stcc/compose-fx [{:dispatch-later [{:ms 1000, :dispatch [:x]}]}
                           {:ms 1000 :dispatch [:y]}]))))


(deftest compose-promotes-to-vector
  (is (= {:dispatch-n [[:x]]}
         (stcc/compose-fx [:x]))))
