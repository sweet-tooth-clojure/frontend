(ns sweet-tooth.frontend.sync.dispatch.ajax
  (:require [re-frame.core :as rf]
            [taoensso.timbre :as timbre]
            [ajax.core :refer [GET PUT POST DELETE]]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [integrant.core :as ig]))

(def request-methods
  {:query  GET
   :get    GET
   :put    PUT
   :update PUT
   :post   POST
   :create POST
   :delete DELETE})

(defn sync-dispatch-fn
  [req-adapter global-opts]
  (fn [req]
    (let [[method res {:keys [uri on-success on-fail] :as opts} :as req-sig] (req-adapter req)
          request-method (get request-methods method)]

      (when-not req-sig
        (timbre/error "could not find route for request"
                      ::no-route-found
                      {:req req})
        (throw (js/Error. "Invalid request: could not find route for request")))
      
      (when-not request-method
        (timbre/error (str "request method did not map to an HTTP request function. valid methods are " (keys request-methods))
                      ::ajax-dispatch-no-request-method
                      {:req req
                       :method method})
        (throw (js/Error. "Invalid request: no request method found")))
      
      ((get request-methods method)
       uri
       (-> global-opts
           (merge opts)
           (assoc :handler       (stsf/sync-success-handler req on-success))
           (assoc :error-handler (stsf/sync-fail-handler req on-fail)))))))

(defmethod ig/init-key ::sync-dispatch-fn
  [_ {:keys [req-adapter global-opts]}]
  (sync-dispatch-fn (or req-adapter identity) global-opts))
