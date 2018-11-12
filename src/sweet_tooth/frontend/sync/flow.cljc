(ns sweet-tooth.frontend.sync.flow
  "Sync provides a layer of indirection between requests to read/write
  data and the mechanism for handling the request. It also provides a
  common interface across those mechanisms."
  (:require [re-frame.core :as rf]
            [ajax.core :refer [GET PUT POST DELETE]]
            [taoensso.timbre :as timbre]
            [sweet-tooth.frontend.handlers :as sth]
            [sweet-tooth.frontend.core.flow :as stcf]
            [integrant.core :as ig]))

(defn req-path
  [[method resource opts]]
  [method resource (or (select-keys opts [:params]) {})])

(defn new-request
  [db req]
  (-> db
      (assoc-in [::reqs (req-path req)] {:state :active})
      (update ::active-request-count (fnil inc 0))))

(defn sync-event-fx
  [cofx req]
  (-> cofx
      (dissoc :event)
      (update :db new-request req)
      (merge {::sync (assoc cofx ::req req)})))

(defn sync-dispatch-fn
  [cofx]
  (get-in cofx [:db
                :sweet-tooth.frontend/config
                ::sync
                :sync-dispatch]))

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

(rf/reg-sub ::sync-state
  (fn [db [_ req]]
    (:state (get-in db [::reqs (req-path req)]))))

(sth/rr rf/reg-event-fx ::sync
  []
  (fn [cofx [_ & [req]]]
    (sync-event-fx cofx req)))

(sth/rr rf/reg-fx ::sync
  (fn [cofx]
    ((sync-dispatch-fn cofx) cofx)))

(sth/rr rf/reg-event-db ::sync-success
  []
  sync-success)

(sth/rr rf/reg-event-db ::sync-fail
  []
  sync-fail)

;; TODO write a schema describing the config that can be sent here
;; TODO possibly add some timeout effect here to clean up sync
(defmethod ig/init-key ::sync
  [_ opts]
  opts)
