(ns sweet-tooth.frontend.form.flow-test
  (:require [sweet-tooth.frontend.form.flow :as sut]
            #?(:clj [clojure.test :refer [is deftest]]
               :cljs [cljs.test :refer [is deftest] :include-macros true])))

(deftest test-input-event
  (is (= {:form {:todos {:create {:input-events {:todo/title #{"change"}},
                                  :buffer       {:todo/title "hi"}}}}}
         (sut/input-event {} [{:partial-form-path [:todos :create]
                               :attr-path         [:todo/title]
                               :val               "hi"
                               :event-type        "change"}])))

  ;; nested atr path
  (is (= {:form {:todos {:create {:input-events {:address {:city #{"change"}}},
                                  :buffer       {:address {:city "hi"}}}}}}
         (sut/input-event {} [{:partial-form-path [:todos :create]
                               :attr-path         [:address :city]
                               :val               "hi"
                               :event-type        "change"}]))))

(deftest test-input-event-validation
  (letfn [(validate [_] {:todo/title #{"must not be empty"}})]
    ;; per-input validation fn
    (is (= {:form {:todos {:create {:input-events {:todo/title #{"change"}},
                                    :buffer       {:todo/title ""}
                                    :errors       {:todo/title #{"must not be empty"}}}}}}
           (sut/input-event {} [{:partial-form-path [:todos :create]
                                 :attr-path         [:todo/title]
                                 :validate          validate
                                 :val               ""
                                 :event-type        "change"}])))

    ;; form-wide validation fn
    (is (= {:form {:todos {:create {:input-events {:todo/title #{"change"}},
                                    :buffer       {:todo/title ""}
                                    :errors       {:todo/title #{"must not be empty"}}
                                    :validate     validate}}}}
           (sut/input-event {:form {:todos {:create {:validate validate}}}}
                            [{:partial-form-path [:todos :create]
                              :attr-path         [:todo/title]
                              :val               ""
                              :event-type        "change"}])))))

(deftest test-reset-form-buffer
  (is (= {:form {:todos {:create {:base   {:todo/title "werp"}
                                  :buffer {:todo/title "werp"}}}}}
         (sut/reset-form-buffer {:form {:todos {:create {:base   {:todo/title "werp"}
                                                         :buffer {:todo/title "what"}}}}}
                                [[:todos :create]]))))

(deftest test-initialize-form
  ;; validates on initialize
  ;; sets base to buffer
  (is (= {:form nil})))


;; initialize form from path
