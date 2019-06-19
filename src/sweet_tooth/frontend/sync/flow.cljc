(ns sweet-tooth.frontend.sync.flow
  "Sync provides a layer of indirection between requests to read/write
  data and the mechanism for handling the request. It also provides a
  common interface across those mechanisms."
  (:require [re-frame.core :as rf]
            [sweet-tooth.frontend.handlers :as sth]
            [sweet-tooth.frontend.core.flow :as stcf]
            [sweet-tooth.frontend.core.utils :as stcu]
            [sweet-tooth.frontend.routes :as stfr]
            [sweet-tooth.frontend.routes.protocol :as strp]
            [integrant.core :as ig]
            [medley.core :as medley]
            [clojure.data :as data]
            [meta-merge.core :refer [meta-merge]]
            [taoensso.timbre :as log]))

;;--------------------
;; request tracking
;;--------------------
(defn req-path
  "Attempts to look up a req-path-fn, falls back on default method for
  getting 'address' of a request in the app-db"
  [[method resource opts :as req]]
  [method resource (or (::req-id opts) (stfr/req-id resource opts))])

(defn track-new-request
  "Adds a request's state te the app-db and increments the activ request
  count"
  [db req]
  (-> db
      (assoc-in [::reqs (req-path req)] {:state :active})
      (update ::active-request-count (fnil inc 0))))

;;------
;; dispatch handler wrappers
;;------
(defn sync-finished
  [db req status]
  (-> db
      (assoc-in [::reqs (req-path req)] {:state status})
      (update ::active-request-count dec)))

(defn sync-success
  [db [_ req]]
  (sync-finished db req :success))

(defn sync-fail
  [db [_ req]]
  (sync-finished db req :fail))

(defn sync-success-handler
  [req [handler-key & args :as dispatch-sig]]
  (fn [resp]
    (rf/dispatch [::sync-success req resp])
    (when dispatch-sig
      (rf/dispatch (into [handler-key resp] args)))))

(defn sync-fail-handler
  [req [handler-key & args :as dispatch-sig]]
  (fn [resp]
    (rf/dispatch [::sync-fail req resp])
    (when dispatch-sig
      ;; TODO this is cljs-ajax specific
      (rf/dispatch (into [handler-key (get-in resp [:response :errors])] args)))))

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

(defn add-default-success-handler
  [req]
  (update req 2 (fn [{:keys [on-success] :as opts}]
                  (if on-success opts (merge opts {:on-success [::default-success-handler req]})))))

(defn adapt-req
  [[method route-name opts :as res] router]
  (when-let [path (strp/path router
                             route-name
                             (or (:route-params opts)
                                 (:params opts)
                                 opts)
                             (:query-params opts))]
    [method route-name (assoc opts :path path)]))

(defn sync-event-fx
  "In response to a sync event, return an effect map of:
  a) updated db to track a sync request
  b) ::sync effect, to be handled by the ::sync effect handler"
  [{:keys [db] :as cofx} req]
  (let [{:keys [router sync-dispatch-fn]} (get-in cofx [:db :sweet-tooth/system ::sync])
        adapted-req                       (adapt-req req router)]
    (if adapted-req
      {:db             (track-new-request db adapted-req)
       ::dispatch-sync {:dispatch-fn sync-dispatch-fn
                        :req         adapted-req}}
      (log/warn "sync could not match req" {:req req}))))

(sth/rr rf/reg-event-db ::default-success-handler
  []
  (fn [db [_ resp req]]
    (if (vector? resp)
      (stcf/update-db db [resp])
      (log/warn "Response was not a vector:" {:resp resp :req (into [] (take 2 req))}))))

(sth/rr rf/reg-event-fx ::sync
  []
  (fn [cofx [_ req]]
    (sync-event-fx cofx (add-default-success-handler req))))

(sth/rr rf/reg-event-fx ::sync-once
  []
  (fn [cofx [_ req]]
    (when-not (= :success (sync-state (:db cofx) req))
      (sync-event-fx cofx (add-default-success-handler req)))))

(sth/rr rf/reg-fx ::dispatch-sync
  (fn [{:keys [dispatch-fn req]}]
    (dispatch-fn req)))

(sth/rr rf/reg-event-db ::sync-success
  []
  sync-success)

(sth/rr rf/reg-event-db ::sync-fail
  []
  sync-fail)

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
  (fn [cofx [call-opts params]]
    {:dispatch [::sync [method endpoint (build-opts opts call-opts params)]]}))

(defn sync-once-fx
  "Returns an effect handler that dispatches a sync event"
  [[method endpoint & [opts]]]
  (fn [cofx [call-opts params]]
    {:dispatch [::sync-once [method endpoint (build-opts opts call-opts params)]]}))

;; TODO possibly add some timeout effect here to clean up sync
(defmethod ig/init-key ::sync
  [_ opts]
  opts)
