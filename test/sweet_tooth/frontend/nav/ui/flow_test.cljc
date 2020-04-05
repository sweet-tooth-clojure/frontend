(ns sweet-tooth.frontend.nav.ui.flow-test
  (:require [sweet-tooth.frontend.nav.ui.flow :as sut]
            #?(:clj [clojure.test :refer [is deftest]]
               :cljs [cljs.test :refer [is deftest] :include-macros true])))

(deftest test-assoc-in-ui
  (is (= {:nav {:ui {:x :y}}}
         (sut/assoc-in-ui {} :x :y))))
