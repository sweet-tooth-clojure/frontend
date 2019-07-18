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
            [sweet-tooth.frontend.paths :as paths]
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
  "Update sync bookkeeping"
  [db [_ req resp]]
  (-> db
      (assoc-in [::reqs (req-path req)] {:state (:type resp)})
      (update ::active-request-count dec)))

(sth/rr rf/reg-event-db ::sync-finished
  []
  sync-finished)

(defn dispatch-lifecycle
  [resp stage lifecycle-name fallback-lifecycle-name]
  (when-let [dispatch-sig (or (get stage lifecycle-name)
                              (get stage fallback-lifecycle-name))]
    (rf/dispatch (conj dispatch-sig (with-meta resp {:sweet-tooth true ::resp true})))))

(defn sync-response-handler
  "Returns a function to handle sync responses"
  [req]
  (fn [{:keys [type] :as resp}]
    (rf/dispatch [::sync-finished req resp])
    (let [{:keys [bf on af]} (get req 2)]
      (dispatch-lifecycle resp bf type :fail)
      (dispatch-lifecycle resp on type :fail)
      (dispatch-lifecycle resp af type :fail))))

;;------
;; registrations
;;------
(defn default-sync-handlers
  "Updates request opts to include default handlers, plus adds common
  args to all handlers"
  [req-opts handler-defaults & [common-args]]
  (let [req-opts (or req-opts {:on {}})]
    (update req-opts :on (fn [handlers]
                           (->> (merge handler-defaults handlers)
                                (medley/map-vals #(into (vec %) common-args)))))))

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

(defn add-default-sync-response-handlers
  [req]
  (update req 2
          default-sync-handlers
          {:success [::default-sync-response-handler]
           :fail    [::default-sync-response-handler]}
          [(with-meta req {:sweet-tooth true ::req true})]))

(defn adapt-req
  [[method route-name opts :as res] router]
  (when-let [path (strp/path router
                             route-name
                             (or (:route-params opts)
                                 (:params opts)
                                 opts)
                             (:query-params opts))]
    [method route-name (assoc opts :path path)]))

(sth/rr rf/reg-event-db ::default-sync-response-handler
  [rf/trim-v]
  (fn [db [req {:keys [response-data]}]]
    (if (vector? response-data)
      (stcf/update-db db response-data)
      (log/warn "Sync response data was not a vector:" {:response-data response-data :req (into [] (take 2 req))}))))

;;-----------------------
;; dispatch sync requests
;;-----------------------
(defn sync-event-fx
  "In response to a sync event, return an effect map of:
  a) updated db to track a sync request
  b) ::dispatch-sync effect, to be handled by the ::dispatch-sync effect handler"
  [{:keys [db] :as cofx} req]
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
  (fn [db [_ {:keys [response-data]}]]
    (stcf/update-db db response-data)))

(sth/rr rf/reg-event-db ::replace-entities
  [rf/trim-v]
  (fn [db [_ {:keys [response-data]}]]
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
