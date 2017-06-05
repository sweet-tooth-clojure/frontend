(ns sweet-tooth.bootstrap
  "This will eventually provide a function to configure all sweet tooth handlers"
  (:require [sweet-tooth.frontend.remote.handlers :as strh]))

(defn bootstrap
  [config]
  (strh/reg-http-event-fx (get-in config [::strh/http :interceptors])))
