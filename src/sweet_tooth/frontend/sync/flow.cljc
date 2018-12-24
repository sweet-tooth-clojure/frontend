(ns sweet-tooth.frontend.sync.flow
  "Sync provides a layer of indirection between requests to read/write
  data and the mechanism for handling the request. It also provides a
  common interface across those mechanisms."
  (:require [re-frame.core :as rf]
            [sweet-tooth.frontend.handlers :as sth]
            [sweet-tooth.frontend.core.flow :as stcf]
            [integrant.core :as ig]
            [medley.core :as medley]
            [clojure.data :as data]
            [clojure.walk :as walk]))

(defn req-path
  "Get the 'address' for a request in the app-db"
  [[method resource opts]]
  [method resource (or (select-keys opts [:params]) {})])

(defn track-new-request
  "Adds a request's state te the app-db and increments the activ request
  count"
  [db req]
  (-> db
      (assoc-in [::reqs (req-path req)] {:state :active})
      (update ::active-request-count (fnil inc 0))))

(defn sync-dispatch-fn
  [cofx]
  (get-in cofx [:db
                :sweet-tooth.frontend/config
                ::sync
                :sync-dispatch-fn]))

;; TODO update this to include the sync-dispatch-fn rather than all of cofx
(defn sync-event-fx
  "In response to a sync event, return an effect map of:
  a) updated db to track a sync request
  b) ::sync effect, to be handled by the ::sync effect handler"
  [cofx req]
  (-> cofx
      (dissoc :event)
      (update :db track-new-request req)
      (assoc ::sync {:req         req
                     :cofx        cofx
                     :dispatch-fn (sync-dispatch-fn cofx)})))


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
  (fn [db [_ req]]
    (sync-state db req)))

(defn projection?
  "Is every value in x present in y?"
  [x y]
  {:pre [(and (seqable? x) (seqable? y))]}
  (let [diff (second (clojure.data/diff y x))]
    (->> (walk/postwalk (fn [x]
                          (when-not (and (map? x)
                                         (nil? (first (vals x))))
                            x))
                        diff)
         (every? nil?))))

(rf/reg-sub ::sync-state-q
  (fn [db [_ query]]
    (medley/filter-keys (partial projection? query) (::reqs db))))

(defn add-default-success-handler
  [req]
  (update req 2 (fn [{:keys [on-success] :as opts}]
                  (if on-success opts (merge opts {:on-success [::stcf/update-db]})))))

(sth/rr rf/reg-event-fx ::sync
  []
  (fn [cofx [_ req]]
    (sync-event-fx cofx (add-default-success-handler req))))

(sth/rr rf/reg-fx ::sync
  (fn [{:keys [dispatch-fn req]}] (dispatch-fn req)))

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
