(ns sweet-tooth.frontend.sync.dispatch.ajax
  (:require [re-frame.core :as rf]
            [taoensso.timbre :as timbre]
            [ajax.core :refer [GET PUT POST DELETE]]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [sweet-tooth.frontend.routes.protocol :as strp]
            [integrant.core :as ig]
            [clojure.set :as set]))

(def request-methods
  {:query  GET
   :get    GET
   :put    PUT
   :update PUT
   :post   POST
   :create POST
   :delete DELETE})

(def fails
  {400 :bad-request
   401 :unauthenticated
   403 :unauthorized
   404 :not-found
   405 :method-not-allowed
   500 :unknown-error
   503 :service-unavailable})

(defn adapt-req
  [[method route-name opts :as res]]
  (if-let [path (:path opts)]
    [method route-name (-> opts
                           (assoc :uri path)
                           (cond-> (empty? (:params opts)) (dissoc :params)))]
    (timbre/warn "Could not resolve route" ::route-not-found {:res          res
                                                              :route-params (:route-params opts)})))

(defn sync-dispatch-fn
  [global-opts]
  (fn [req]
    (let [[method res {:keys [uri] :as opts} :as req-sig] (adapt-req req)
          request-method                                  (get request-methods method)]

      (when-not req-sig
        (timbre/error "could not find route for request"
                      ::no-route-found
                      {:req req})
        (throw (js/Error. "Invalid request: could not find route for request")))
      
      (when-not request-method
        (timbre/error (str "request method did not map to an HTTP request function. valid methods are " (keys request-methods))
                      ::ajax-dispatch-no-request-method
                      {:req    req
                       :method method})
        (throw (js/Error. "Invalid request: no request method found")))
      
      ((get request-methods method)
       uri
       (-> global-opts
           (merge opts)
           (assoc :handler       (fn [resp]
                                   ((stsf/sync-response-handler req)
                                    {:type          :success
                                     :response-data resp})))
           (assoc :error-handler (fn [resp]
                                   ((stsf/sync-response-handler req)
                                    (-> resp
                                        (assoc :type :fail)
                                        (set/rename-keys {:response :response-data}))))))))))

(defmethod ig/init-key ::sync-dispatch-fn
  [_ {:keys [global-opts]}]
  (sync-dispatch-fn global-opts))
