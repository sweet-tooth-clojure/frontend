(ns sweet-tooth.frontend.form.utils-test
  (:require [sweet-tooth.frontend.form.utils :as sut]
            [clojure.string :as str]
            #?(:clj [clojure.test :refer [is deftest testing]]
               :cljs [cljs.test :refer [is deftest testing] :include-macros true])))

(deftest test-update-in-form
  (testing "applies update on a form attr"
    (is (= {:form {:post {:todos {:buffer {:todo/title "HI!"}}}}}
           (sut/update-in-form {:form {:post {:todos {:buffer {:todo/title "hi!"}}}}}
                               [:post :todos]
                               :todo/title
                               str/upper-case)))))
