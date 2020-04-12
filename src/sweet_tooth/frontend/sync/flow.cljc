(ns sweet-tooth.frontend.sync.flow
  "Sync provides a layer of indirection between requests to read/write
  data and the mechanism for handling the request. It also provides a
  common interface across those mechanisms."
  (:require [re-frame.core :as rf]
            [sweet-tooth.frontend.handlers :as sth]
            [sweet-tooth.frontend.core.flow :as stcf]
            [sweet-tooth.frontend.core.utils :as stcu]
            [sweet-tooth.frontend.core.compose :as stcc]
            [sweet-tooth.frontend.routes :as stfr]
            [sweet-tooth.frontend.routes.protocol :as strp]
            [sweet-tooth.frontend.paths :as paths]
            [sweet-tooth.frontend.failure.flow :as stfaf]
            [integrant.core :as ig]
            [medley.core :as medley]
            [clojure.walk :as walk]
            [meta-merge.core :refer [meta-merge]]
            [taoensso.timbre :as log]))

;;--------------------
;; request tracking
;;--------------------
(defn req-path
  "returns a 'normalized' req path for a request"
  [[method resource opts]]
  [method resource (or (::req-id opts) (stfr/req-id resource opts))])

(defn track-new-request
  "Adds a request's state te the app-db and increments the active request
  count"
  [db req]
  (-> db
      (assoc-in [::reqs (req-path req)] {:state :active})
      (update ::active-request-count (fnil inc 0))))

;;------
;; dispatch handler wrappers
;;------
(defn sync-finished
  "Update sync bookkeeping"
  [db [_ req resp]]
  (-> db
      (assoc-in [::reqs (req-path req)] {:state (:status resp)})
      (update ::active-request-count dec)))

(sth/rr rf/reg-event-db ::sync-finished
  []
  sync-finished)

(defn sync-response-handler
  "Returns a function to handle sync responses"
  [req]
  (fn [{:keys [status] :as resp}]
    (let [{:keys [on]} (get req 2)
          $ctx         (assoc (get on :$ctx {})
                              :resp resp
                              :req  req)
          composed-fx  (stcc/compose-fx (get on status (get on :fail)))]
      (rf/dispatch [::stcc/compose-dispatch
                    [[::sync-finished req resp]
                     (walk/postwalk (fn [x] (if (= x :$ctx) $ctx x))
                                    composed-fx)]]))))

;;------
;; registrations
;;------
(defn sync-state
  [db req]
  (get-in db [::reqs (req-path req) :state]))

(rf/reg-sub ::sync-state
  (fn [db [_ req comparison]]
    (let [state (sync-state db req)]
      (if comparison (= state comparison) state))))

(rf/reg-sub ::sync-state-q
  (fn [db [_ query]]
    (medley/filter-keys (partial stcu/projection? query) (::reqs db))))

(defn adapt-req
  "Makes sure a path is findable from req and adds it"
  [[method route-name opts :as _res] router]
  (when-let [path (strp/path router
                             route-name
                             (or (:route-params opts)
                                 (:params opts)
                                 opts)
                             (:query-params opts))]
    [method route-name (assoc opts :path path)]))

(sth/rr rf/reg-event-db ::default-sync-success
  [rf/trim-v]
  (fn [db [{{:keys [response-data]} :resp
            :keys [req]}]]
    (if (vector? response-data)
      (stcf/update-db db response-data)
      (do (log/warn "Sync response data was not a vector:" {:response-data response-data
                                                            :req           (into [] (take 2 req))})
          db))))

;; TODO check that this does what I think
;; is the second argument here correct?
(sth/rr rf/reg-event-fx ::default-sync-fail
  [rf/trim-v]
  (fn [{:keys [db] :as _cofx} [{:keys [req], {:keys [response-data]} :resp}]]
    (let [sync-info {:response-data response-data :req (into [] (take 2 req))}]
      {:db       (if (vector? response-data)
                   (stcf/update-db db response-data)
                   (do (log/warn "Sync response data was not a vector:" sync-info)
                       db))
       :dispatch [::stfaf/add-failure [:sync sync-info]]})))

;;-----------------------
;; dispatch sync requests
;;-----------------------

(defn add-default-sync-response-handlers
  [req]
  (-> req
      (update-in [2 :on] (partial merge {:success [::default-sync-success :$ctx]
                                         :fail    [::default-sync-fail :$ctx]}))
      (update-in [2 :on] meta-merge {:$ctx {:req req}})))


(defn sync-event-fx
  "In response to a sync event, return an effect map of:
  a) updated db to track a sync request
  b) ::dispatch-sync effect, to be handled by the ::dispatch-sync effect handler"
  [{:keys [db] :as _cofx} req]
  (let [{:keys [router sync-dispatch-fn]} (paths/get-path db :system ::sync)
        adapted-req                       (-> req
                                              (add-default-sync-response-handlers)
                                              (adapt-req router))]
    (if adapted-req
      {:db             (track-new-request db adapted-req)
       ::dispatch-sync {:dispatch-fn sync-dispatch-fn
                        :req         adapted-req}}
      (log/warn "sync router could not match req" {:req req}))))

(sth/rr rf/reg-event-fx ::sync
  []
  (fn [cofx [_ req]]
    (sync-event-fx cofx req)))

(sth/rr rf/reg-event-fx ::sync-once
  []
  (fn [cofx [_ req]]
    (when-not (= :success (sync-state (:db cofx) req))
      (sync-event-fx cofx req))))

(sth/rr rf/reg-fx ::dispatch-sync
  (fn [{:keys [dispatch-fn req]}]
    (dispatch-fn req)))

;;---------------
;; sync responses
;;---------------
;; Unwraps response-data for update-db
(sth/rr rf/reg-event-db ::update-db
  [rf/trim-v]
  (fn [db [{{:keys [response-data]} :resp}]]
    (stcf/update-db db response-data)))

(sth/rr rf/reg-event-db ::replace-entities
  [rf/trim-v]
  (fn [db [{{:keys [response-data]} :resp}]]
    (stcf/replace-entities db response-data)))

;;------
;; event helpers
;;------
(defn build-opts
  [opts call-opts params]
  (let [{:keys [route-params params] :as new-opts} (-> (meta-merge opts call-opts)
                                                       (update :params meta-merge params))]
    (cond-> new-opts
      (not route-params) (assoc :route-params params))))

(defn sync-fx
  "Returns an effect handler that dispatches a sync event"
  [[method endpoint & [opts]]]
  (fn [_cofx [call-opts params]]
    {:dispatch [::sync [method endpoint (build-opts opts call-opts params)]]}))

(defn sync-once-fx
  "Returns an effect handler that dispatches a sync event"
  [[method endpoint & [opts]]]
  (fn [_cofx [call-opts params]]
    {:dispatch [::sync-once [method endpoint (build-opts opts call-opts params)]]}))

;; TODO possibly add some timeout effect here to clean up sync
(defmethod ig/init-key ::sync
  [_ opts]
  opts)

;;------
;; db patch exception
;;------
(defn db-patch-handle-exception
  [db ex-data]
  #?(:cljs (js/console.warn "sync exception" ex-data))
  db)

;;---------------
;; common sync
;;---------------
(defn- method-sync-fx
  [method]
  (fn [cofx req]
    (sync-event-fx cofx (into [method] req))))

(sth/rr rf/reg-event-fx ::get
  [rf/trim-v]
  (method-sync-fx :get))

(sth/rr rf/reg-event-fx ::put
  [rf/trim-v]
  (method-sync-fx :put))

(sth/rr rf/reg-event-fx ::post
  [rf/trim-v]
  (method-sync-fx :post))

(sth/rr rf/reg-event-fx ::delete
  [rf/trim-v]
  (method-sync-fx :delete))
