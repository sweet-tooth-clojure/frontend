(ns sweet-tooth.frontend.sync.flow-test
  (:require [sweet-tooth.frontend.sync.flow :as sut]
            [sweet-tooth.frontend.routes.protocol :as strp]
            #?(:cljs [sweet-tooth.frontend.routes.reitit :as strr])
            #?(:clj [clojure.test :refer [is deftest testing]]
               :cljs [cljs.test :refer [is deftest testing] :include-macros true])))

(deftest test-track-new-request
  (is (= #:sweet-tooth.frontend.sync.flow{:reqs                 {[:get :home {}] {:state :active}}
                                          :active-request-count 1}
         (sut/track-new-request {} [:get :home {::sut/req-id {}}]))))

(deftest test-sync-finished
  (is (= #:sweet-tooth.frontend.sync.flow{:reqs                 {[:get :home {}] {:state :ok}}
                                          :active-request-count 0}
         (-> {}
             (sut/track-new-request [:get :home {::sut/req-id {}}])
             (sut/sync-finished [nil
                                 [:get :home {::sut/req-id {}}]
                                 {:status :ok}])))))

;; why am i doing this, it's kinda whack
#?(:cljs
   (def router
     (strp/router (merge strr/config-defaults
                         {:routes [["/"
                                    {:name :home}]

                                   ["/todo-list/:id"
                                    {:name :todo-list}]]}))))

#?(:cljs
   (deftest test-adapt-req
     (is (= [:get :todo-list {:route-params {:id 1}
                              :path         "/todo-list/1"}]
            (sut/adapt-req [:get :todo-list {:route-params {:id 1}}] router)))))
