(ns sweet-tooth.frontend.form.flow-test
  (:require [sweet-tooth.frontend.form.flow :as sut]
            #?(:clj [clojure.test :refer [is deftest]]
               :cljs [cljs.test :refer [is deftest] :include-macros true])))

(deftest test-input-event
  (is (= {:form {:todos {:create {:input-events #:todo{:title #{"blur"}},
                                  :buffer       #:todo{:title "hi"}}}}}
         (sut/input-event {} [{:partial-form-path [:create :todo]
                               :attr-path         [:todo/title]
                               :val               "hi"
                               :event-type        "blur"}]))))
