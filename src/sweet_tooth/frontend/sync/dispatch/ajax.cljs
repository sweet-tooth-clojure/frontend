(ns sweet-tooth.frontend.sync.dispatch.ajax
  "Takes sync request and dispatch AJAX requests"
  (:require [taoensso.timbre :as timbre]
            [ajax.core :refer [GET HEAD POST PUT DELETE OPTIONS TRACE PATCH PURGE]]
            [ajax.transit :as at]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [integrant.core :as ig]
            [clojure.set :as set]
            [cognitect.anomalies :as anom]))

(def request-methods
  {:query   GET
   :get     GET
   :put     PUT
   :update  PUT
   :post    POST
   :create  POST
   :delete  DELETE
   :options OPTIONS
   :trace   TRACE
   :patch   PATCH
   :purge   PURGE
   :head    HEAD})

(def segments-response-format
  {:read (at/transit-read-fn {})
   :description "Segments over Transit"
   :content-type ["application/st-segments+json"]})

(def fails
  {400 ::anom/incorrect
   401 ::anom/forbidden
   403 ::anom/forbidden
   404 ::anom/not-found
   405 ::anom/unsupported
   500 ::anom/fault
   503 ::anom/unavailable})

(defn adapt-req
  "Adapts the req opts as passed in by sync so that they'll work with
  cljs-ajax"
  [[method route-name opts :as res]]
  (if-let [path (:path opts)]
    [method route-name (-> opts
                           (assoc :uri path)
                           (cond-> (empty? (:params opts)) (dissoc :params)))]
    (timbre/warn "Could not resolve route" ::route-not-found {:res          res
                                                              :route-params (:route-params opts)})))

(defn sync-dispatch-fn
  [{:keys [fail-map]
    :or   {fail-map fails}
    :as   global-opts}]
  (fn [req]
    (let [[method _res {:keys [uri] :as opts} :as req-sig] (adapt-req req)
          request-method                                   (get request-methods method)]

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
       (-> {:response-format segments-response-format}
           (merge global-opts opts)
           (assoc :handler       (fn [resp]
                                   ((stsf/sync-response-handler req)
                                    {:status        :success
                                     :response-data resp})))
           (assoc :error-handler (fn [resp]
                                   ((stsf/sync-response-handler req)
                                    (-> resp
                                        (assoc :status (get fail-map (:status resp) :fail))
                                        (set/rename-keys {:response :response-data}))))))))))

(defmethod ig/init-key ::sync-dispatch-fn
  [_ {:keys [global-opts]}]
  (sync-dispatch-fn global-opts))
