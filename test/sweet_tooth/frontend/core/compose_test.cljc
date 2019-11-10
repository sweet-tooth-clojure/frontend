(ns sweet-tooth.frontend.core.compose-test
  (:require [sweet-tooth.frontend.core.compose :as stcc]
            #?(:clj [clojure.test :as t :refer [is deftest]]
               :cljs [cljs.test :as t :include-macros true :refer [is deftest]])))

(deftest composes-nothing
  (is (= {}
         (stcc/compose-fx []))))

(deftest composes-dispatch-n
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
