(ns sweet-tooth.frontend.routes.reitit-test
  (:require [sweet-tooth.frontend.routes.reitit :as sut]
            [sweet-tooth.frontend.routes.protocol :as strp]
            [cljs.test :refer [is deftest testing] :include-macros true]))

(deftest routing
  (let [router (strp/router (merge sut/config-defaults
                                   {:routes [["/"
                                              {:name :home}]

                                             ["/todo-list/:id"
                                              {:name :todo-list}]]}))]
    (testing "returns route from path"
      (is (= {:template     "/"
              :result       nil
              :path-params  {}
              :path         "/"
              :query-params {}
              :route-name   :home
              :params       {}}
             (strp/route router "/"))))

    (testing "returns a req-id"
      (is (= {:id 1}
             (strp/req-id router :todo-list {:route-params {:id 1}}))))

    (testing "returns path for route name"
      (is (= "/"
             (strp/path router :home))))))
