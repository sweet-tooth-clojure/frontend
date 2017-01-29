(ns sweet-tooth.frontend.remote.handlers
  (:require [re-frame.core :refer [reg-fx dispatch]]
            [ajax.core :refer [GET PUT POST DELETE]]))

(defn ajax-success
  "Dispatch result of ajax call to handler key"
  [[handler-key & args]]
  (fn [response]
    (pr "response:" response)
    (dispatch (into [handler-key response] args))))

;; TODO spec that error response expects :errors key
(defn ajax-error
  "Dispatch error returned by ajax call to handler key"
  [[handler-key & args]]
  (fn [response]
    (pr "error response:" response)
    (dispatch (into [handler-key (get-in response [:response :errors])] args))))

(reg-fx ::http
  (fn [{:keys [method url on-success on-fail data token]}]
    (method url
            (cond-> {}
              data       (assoc :params data)
              on-success (assoc :handler (ajax-success on-success))
              on-fail    (assoc :error-handler (ajax-error on-fail))
              token      (assoc :headers {"Authorization" (str "Token " token)})))))

