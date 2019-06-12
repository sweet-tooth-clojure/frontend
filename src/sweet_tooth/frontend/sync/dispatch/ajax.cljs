(ns sweet-tooth.frontend.sync.dispatch.ajax
  (:require [re-frame.core :as rf]
            [taoensso.timbre :as timbre]
            [ajax.core :refer [GET PUT POST DELETE]]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [sweet-tooth.frontend.routes.protocol :as strp]
            [integrant.core :as ig]))

(def request-methods
  {:query  GET
   :get    GET
   :put    PUT
   :update PUT
   :post   POST
   :create POST
   :delete DELETE})

(defn adapt-req
  [[method route-name opts :as res] router]
  (if-let [path (strp/path router route-name (:route-params opts) (:query-params opts))]
    [method route-name (-> opts
                           (assoc :uri path)
                           (cond-> (empty? (:params opts)) (dissoc :params)))]
    (timbre/warn "Could not resolve route" ::route-not-found {:res          res
                                                              :route-params (:route-params opts)})))

(defn sync-dispatch-fn
  [router global-opts]
  (fn [req]
    (let [[method res {:keys [uri on-success on-fail] :as opts} :as req-sig] (adapt-req req router)
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
  [_ {:keys [req-adapter global-opts router]}]
  (sync-dispatch-fn router global-opts))
