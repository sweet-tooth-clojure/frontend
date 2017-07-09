(ns sweet-tooth.bootstrap
  "This will eventually provide a function to configure all sweet tooth handlers"
  (:require [sweet-tooth.frontend.remote.flow :as strf]))

(defn bootstrap
  [config]
  (strf/reg-http-event-fx (get-in config [::strf/http :interceptors])))
