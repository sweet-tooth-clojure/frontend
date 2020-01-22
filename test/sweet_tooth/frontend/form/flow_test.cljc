(ns sweet-tooth.frontend.form.flow-test
  (:require [sweet-tooth.frontend.form.flow :as sut]
            #?(:clj [clojure.test :refer [is deftest testing]]
               :cljs [cljs.test :refer [is deftest testing] :include-macros true])))

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
                                                         :buffer {:todo/title "hi"}}}}}
                                [[:todos :create]]))))

(deftest test-initialize-form
  (testing "sets base to buffer"
    (is (= {:form {:todos {:create {:base   {:todo/title "boop"}
                                    :buffer {:todo/title "boop"}}}}}
           (sut/initialize-form {} [[:todos :create] {:buffer {:todo/title "boop"}}]))))

  (testing "validates on initialize"
    (letfn [(validate [_] {:todo/title #{"must not be empty"}})]
      (is (= {:form {:todos {:create {:base     nil
                                      :validate validate
                                      :errors   {:todo/title #{"must not be empty"}}}}}}
             (sut/initialize-form {} [[:todos :create] {:validate validate}]))))))


;; initialize form from path
(deftest test-initialize-form-from-path
  (testing "sets buffer from db path"
    (is (= {:form   {:todos {:create {:base   {:todo/title "boop"}
                                      :buffer {:todo/title "boop"}}}}
            :entity {:todo {1 {:todo/title "boop"}}}}
           (sut/initialize-form-from-path {:entity {:todo {1 {:todo/title "boop"}}}}
                                          [[:todos :create]
                                           {:data-path [:entity :todo 1]}]))))

  (testing "transforms data with data-fn"
    (is (= {:form   {:todos {:create {:base   {:todo/title "boop"
                                               :todo/id    1}
                                      :buffer {:todo/title "boop"
                                               :todo/id    1}}}}
            :entity {:todo {1 {:todo/title "boop"}}}}
           (sut/initialize-form-from-path {:entity {:todo {1 {:todo/title "boop"}}}}
                                          [[:todos :create]
                                           {:data-path [:entity :todo 1]
                                            :data-fn   #(assoc % :todo/id 1)}])))))

(deftest test-clear-form
  (is (= {:form {:todos {:create nil}}}
         (sut/clear-form {:form {:todos {:create {:base   {:todo/title "boop"}
                                                  :buffer {:todo/title "boop"}}}}}
                         [:todos :create]))))

(deftest test-form-sync-opts
  (is (= [:create
          :todos
          {:params       {:todo/title "boop"}
           :route-params {:todo/title "boop"}
           :on           {:success [::sut/submit-form-success :$ctx]
                          :fail    [:sweet-tooth.frontend.form.flow/submit-form-fail :$ctx],
                          :$ctx    {:full-form-path [:form :todos :create]
                                    :form-spec      {}}}}]
         (sut/form-sync-opts [:form :todos :create]
                             {:todo/title "boop"}
                             {}))))
