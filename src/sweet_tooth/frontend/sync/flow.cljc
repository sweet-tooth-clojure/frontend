(ns sweet-tooth.frontend.sync.flow
  "Sync provides a layer of indirection between requests to read/write
  data and the mechanism for handling the request. It also provides a
  common interface across those mechanisms."
  (:require [re-frame.core :as rf]
            [taoensso.timbre :as timbre]
            [sweet-tooth.frontend.handlers :as sth]
            [sweet-tooth.frontend.core.flow :as stcf]
            [sweet-tooth.frontend.core.utils :as stcu]
            [integrant.core :as ig]
            [medley.core :as medley]
            [clojure.data :as data]))

;;--------------------
;; configured behavior
;;--------------------
;;
;; these functions are defined in the integrant, which is stored in the db

(defn sync-dispatch-fn
  [cofx]
  (get-in cofx [:db :sweet-tooth/system ::sync :sync-dispatch-fn]))

(defn req-path-fn
  "Returns a function the can be used to identify a request. Identifying
  requests is used to track request state: active, success, failure"
  [db]
  (get-in db [:sweet-tooth/system ::sync :req-path-fn]))

(defn res-id
  [opts]
  (if (map? opts)
    (or ((some-fn #(get-in % [:params :db/id])
                  #(get-in % [:params :id])
                  #(get-in % [:db/id])
                  #(get-in % [:id]))
         opts)
        nil)
    opts))

(defn req-path
  "Attempts to look up a req-path-fn, falls back on efault method for
  getting 'address' of a request in the app-db"
  [db [method resource opts :as req]]
  (if-let [req-path-f (req-path-fn db)]
    (req-path-f req)
    [method resource (res-id opts)]))

;;--------------------
;; request tracking
;;--------------------
(defn track-new-request
  "Adds a request's state te the app-db and increments the activ request
  count"
  [db req]
  (-> db
      (assoc-in [::reqs (req-path db req)] {:state :active})
      (update ::active-request-count (fnil inc 0))))

;;------
;; dispatch handler wrappers
;;------
(defn sync-finished
  [db req status]
  (-> db
      (assoc-in [::reqs (req-path db req)] {:state status})
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
  (get-in db [::reqs (req-path db req) :state]))

(rf/reg-sub ::sync-state
  (fn [db [_ req]]
    (sync-state db req)))

(rf/reg-sub ::sync-state-q
  (fn [db [_ query]]
    (medley/filter-keys (partial stcu/projection? query) (::reqs db))))

(defn add-default-success-handler
  [req]
  (update req 2 (fn [{:keys [on-success] :as opts}]
                  (if on-success opts (merge opts {:on-success [::stcf/update-db]})))))

(defn sync-event-fx
  "In response to a sync event, return an effect map of:
  a) updated db to track a sync request
  b) ::sync effect, to be handled by the ::sync effect handler"
  [{:keys [db] :as cofx} req]
  {:db    (track-new-request db req)
   ::sync {:dispatch-fn (sync-dispatch-fn cofx)
           :req         req}})

(sth/rr rf/reg-event-fx ::sync
  []
  (fn [cofx [_ req]]
    (sync-event-fx cofx (add-default-success-handler req))))

(sth/rr rf/reg-fx ::sync
  (fn [{:keys [dispatch-fn req]}]
    (timbre/debug "sync effect" ::sync {:req req})
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
(defn sync-fx
  "Returns an effect handler that dispatches a sync event"
  [[method endpoint & [opts]]]
  (fn [cofx [params]]
    {:dispatch [::sync [method endpoint (update opts :params merge params)]]}))

;; TODO possibly add some timeout effect here to clean up sync
(defmethod ig/init-key ::sync
  [_ opts]
  opts)
