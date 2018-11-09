(ns sweet-tooth.frontend.sync.dispatch.ajax
  (:require [re-frame.core :as rf]
            [ajax.core :refer [GET PUT POST DELETE]]
            [taoensso.timbre :as timbre]
            [sweet-tooth.frontend.core :as stc]
            [sweet-tooth.frontend.core.flow :as stcf]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [integrant.core :as ig]))

(def request-methods
  {:get    GET
   :put    PUT
   :post   POST
   :delete DELETE})

(defn sync
  [{:keys [::stsf/req]}]
  (let [[method _ {:keys [uri on-success on-fail] :as opts}] req]
    ((get request-methods method)
     uri
     (-> opts
         (assoc :handler       (stsf/sync-success-handler req on-success))
         (assoc :error-handler (stsf/sync-fail-handler req on-fail))))))

(defmethod ig/init-key ::sync
  [_ _]
  sync)
