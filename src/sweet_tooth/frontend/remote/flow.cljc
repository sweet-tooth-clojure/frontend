(ns sweet-tooth.frontend.remote.flow
  (:require [re-frame.core :refer [reg-fx reg-event-fx dispatch]]
            [ajax.core :refer [GET PUT POST DELETE]]
            [taoensso.timbre :as timbre]))

(defn ajax-success
  "Dispatch result of ajax call to handler key"
  [[handler-key & args]]
  (fn [response]
    (timbre/trace "response:" response)
    (dispatch (into [handler-key response] args))))

;; TODO spec that error response expects :errors key
(defn ajax-error
  "Dispatch error returned by ajax call to handler key"
  [[handler-key & args]]
  (fn [response]
    (timbre/info "error response:" response)
    (dispatch (into [handler-key (get-in response [:response :errors])] args))))

(defn reg-http-event-fx
  [interceptors]
  (reg-event-fx ::http
    interceptors
    (fn [coeffects [_ request-opts]]
      (merge (dissoc coeffects :event) {::http request-opts}))))

(reg-fx ::http
  (fn [{:keys [method url on-success on-fail] :as opts}]
    (let [opts (dissoc opts :method :url :on-success :on-fail)]
      (method url
              (cond-> opts
                on-success (assoc :handler (ajax-success on-success))
                on-fail    (assoc :error-handler (ajax-error on-fail)))))))
