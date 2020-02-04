(ns sweet-tooth.frontend.form.flow-test
  (:require [sweet-tooth.frontend.form.flow :as sut]
            [sweet-tooth.frontend.test-config :as test-config]
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
                             {})))

  (is (= [:create
          :todos
          {:params       {:todo/title "boop"}
           :route-params {:todo/title "boop"}
           :on           {:success [[:sweet-tooth.frontend.form.flow/submit-frm-success :$ctx]
                                    [:do-a-thing :$ctx]]
                          :fail    [:sweet-tooth.frontend.form.flow/submit-form-fail :$ctx],
                          :$ctx    {:full-form-path [:form :todos :create]
                                    :form-spec      {:sync {:on {:success [[::sut/submit-frm-success :$ctx]
                                                                           [:do-a-thing :$ctx]]}}}}}}]
         (sut/form-sync-opts [:form :todos :create]
                             {:todo/title "boop"}
                             {:sync {:on {:success [[::sut/submit-frm-success :$ctx]
                                                    [:do-a-thing :$ctx]]}}}))))

(deftest test-submit-form
  (testing "sets form state to submitting, clears errors, returns sync dispatch event"
    (is (= {:db       {:form {:create {:todos {:state        :submitting
                                               :errors       nil
                                               :input-events #:sweet-tooth.frontend.form.flow{:form #{"submit"}}}}}},
            :dispatch [:sweet-tooth.frontend.sync.flow/sync
                       [:todos :create {:params       nil
                                        :route-params nil
                                        :on           {:success [:sweet-tooth.frontend.form.flow/submit-form-success :$ctx]
                                                       :fail    [:sweet-tooth.frontend.form.flow/submit-form-fail :$ctx]
                                                       :$ctx    {:full-form-path [:form :create :todos]
                                                                 :form-spec      nil}}}]]}
           (sut/submit-form {:db {:form {:create {:todos {:state  nil
                                                          :errors {}}}}}}
                            [[:create :todos]])))))

(deftest test-delete-entity-optimistic-fn
  (testing "dissocs from :entity and returns delete dispatch"
    (let [handler (sut/delete-entity-optimistic-fn :todo :db/id)]
      (is (= {:dispatch [:sweet-tooth.frontend.sync.flow/sync [:delete :todo {:db/id        1
                                                                              :params       nil
                                                                              :route-params nil}]]
              :db       {:entity {:todo {}}}}
             (handler {:db {:entity {:todo {1 {:db/id 1}}}}}
                      [{:db/id 1}]))))))

;; tests how the form gets updated
(deftest test-submit-form-success
  (letfn [(expected [form-map]
            {:db (merge test-config/base-system
                        {:entity {:todo {1 {:db/id 1}}}
                         :form   {:create {:todos form-map}}})})
          (submit-form-success [options]
            (sut/submit-form-success {:db (merge test-config/base-system
                                                 {:form {:create {:todos {:buffer {:todo/title "hi"}}}}})}
                                     [{:full-form-path [:form :create :todos]
                                       :resp           {:response-data [[:entity {:todo {1 {:db/id 1}}}]]}}
                                      options]))]
    (testing "basic form submission"
      (is (= (expected {:buffer {:todo/title "hi"}})
             (submit-form-success nil))))

    (testing "clear all keys"
      (is (= (expected {})
             (submit-form-success {:clear :all}))))

    (testing "clear specified keys"
      (is (= (expected {})
             (submit-form-success {:clear [:buffer]}))))

    (testing "keep key not specified for clearing"
      (is (= (expected {:buffer {:todo/title "hi"}})
             (submit-form-success {:clear [:base]}))))

    (testing "keep specified keys"
      (is (= (expected {:buffer {:todo/title "hi"}})
             (submit-form-success {:keep [:buffer]}))))

    (testing "clear keys not specified for keeping"
      (is (= (expected {})
             (submit-form-success {:keep [:base]}))))))
