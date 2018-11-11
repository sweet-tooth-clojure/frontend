(ns sweet-tooth.frontend.sync.dispatch.ajax
  (:require [re-frame.core :as rf]
            [ajax.core :refer [GET PUT POST DELETE]]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [integrant.core :as ig]))

(def request-methods
  {:get    GET
   :put    PUT
   :post   POST
   :delete DELETE})

(defn sync-fn
  [req-adapter]
  (fn [{:keys [::stsf/req]}]
    (let [[method res {:keys [uri on-success on-fail] :as opts}] (req-adapter req)]
      ((get request-methods method)
       uri
       (-> opts
           (assoc :handler       (stsf/sync-success-handler req on-success))
           (assoc :error-handler (stsf/sync-fail-handler req on-fail)))))))

(defmethod ig/init-key ::sync
  [_ {:keys [req-adapter]}]
  (sync-fn (or req-adapter identity)))
