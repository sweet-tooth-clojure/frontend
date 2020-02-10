(ns sweet-tooth.frontend.routes.reitit-test
  (:require [sweet-tooth.frontend.routes.reitit :as sut]
            [sweet-tooth.frontend.routes.protocol :as strp]
            [cljs.test :refer [is deftest testing] :include-macros true]))

(deftest routing
  (let [router (strp/router (merge sut/config-defaults
                                   {:routes [["/"
                                              {:name     :home
                                               :required [:xyz]}]]}))]
    (is (= {:template     "/"
            :result       nil
            :path-params  {}
            :path         "/"
            :query-params {}
            :route-name   :home
            :params       {}
            :required     [:xyz]}
           (strp/route router "/")))

    (is (= {:xyz 1}
           (strp/req-id router :home {:params {:xyz 1}})))

    (is (= "/"
           (strp/path router :home)))))
