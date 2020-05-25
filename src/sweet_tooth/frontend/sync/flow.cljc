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
            [sweet-tooth.frontend.specs :as ss]
            [integrant.core :as ig]
            [medley.core :as medley]
            [clojure.walk :as walk]
            [meta-merge.core :refer [meta-merge]]
            [taoensso.timbre :as log]
            [clojure.spec.alpha :as s]))


;;--------------------
;; specs
;;--------------------

;; `req` specs
;; The `::sync` and `::sync-once` handlers expect a `req` as their argument
(s/def ::req-method keyword?)
(s/def ::route-name keyword?)

(s/def ::params map?)
(s/def ::route-params ::params)
(s/def ::query-params ::params)

(s/def ::req-opts (s/keys :opt-un [::route-params ::query-parms ::params]))

(s/def ::req (s/cat :req-method ::req-method
                    :route-name ::route-name
                    :req-opts   (s/? ::req-opts)))

;; `::dispatch-sync` specs
(s/def ::dispatch-fn fn?)
(s/def ::dispatch-sync (s/keys :req-un [::req ::dispatch-fn]))

;;--------------------
;; request tracking
;;--------------------
(defn req-path
  "returns a 'normalized' req path for a request.

  normalized in the sense that when it comes to distinguishing requess
  in order to track them, some of the variations between requests are
  significant, and some aren't.

  The first two elements of the request, `method` and `route`, are
  always significant. Where things get tricky is with `opts`. We don't
  want to use `opts` itself because the variation would lead to
  \"identical\" requests being treated as separate, so we use
  `stfr/req-id` to select a subset of opts to distinguish reqs"
  [[method route opts]]
  (or (::req-path opts)
      [method route (or (::req-id opts) (stfr/req-id route opts))]))

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

;; Used to find, e.g. all requests like [:get :topic] or [:post :host]
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

(def default-handlers
  {:default-on {:success [[::default-sync-success :$ctx]]
                :fail    [[::default-sync-fail :$ctx]]}})

;;---
;; helpers

(defn add-default-sync-response-handlers
  [req]
  (update req 2 #(meta-merge default-handlers {:$ctx {:req req}} %)))

(defn reconcile-default-handlers
  "Adds default handlers"
  [req]
  (let [{:keys [default-on on] :as opts} (get req 2)]
    (assoc req 2 (reduce-kv (fn [opts handler-name default-handler-events]
                              (assoc-in opts [:on handler-name] (let [on-events (handler-name on)]
                                                                  (if (vector? default-handler-events)
                                                                    (stcc/compose-events default-handler-events on-events)
                                                                    on-events))))
                            opts
                            default-on))))


(defn sync-event-fx
  "Transforms sync events adding defaults and other options needed for
  the `::dispatch-sync` effect handler to perform a sync.

  returns an effect map of:
  a) updated db to track a sync request
  b) ::dispatch-sync effect, to be handled by the ::dispatch-sync
  effect handler"
  [{:keys [db] :as _cofx} req]
  (let [{:keys [router sync-dispatch-fn]} (paths/get-path db :system ::sync)
        adapted-req                       (-> req
                                              (add-default-sync-response-handlers)
                                              (reconcile-default-handlers)
                                              (adapt-req router))]
    (if adapted-req
      {:db             (track-new-request db adapted-req)
       ::dispatch-sync {:dispatch-fn sync-dispatch-fn
                        :req         adapted-req}}
      (do (log/warn "sync router could not match req" {:req req})
          {:db db}))))

(s/fdef sync-event-fx
  :args (s/cat :cofx ::ss/cofx :req ::req)
  :ret  (s/keys :req-un [::ss/db]
                :req    [::dispatch-sync]))

(defn sync-entity-req
  "To be used when dispatching a sync event for an entity:
  (sync-entity-req :put :comment {:id 1 :content \"comment\"})"
  [[method route ent & [opts]]]
  [method route (-> opts
                    (update :params #(or % ent))
                    (update :route-params #(or % ent)))])
;;---
;; handlers

;; The core event handler for syncing
(sth/rr rf/reg-event-fx ::sync
  [rf/trim-v]
  (fn [cofx [req]]
    (sync-event-fx cofx req)))

;; makes it a little easier to sync a single entity
(sth/rr rf/reg-event-fx ::sync-entity
  [rf/trim-v]
  (fn [cofx [req]]
    (sync-event-fx cofx (sync-entity-req req))))

;; Like `::sync`, but only fires if there hasn't previously been a
;; successful request with the same signature
(sth/rr rf/reg-event-fx ::sync-once
  [rf/trim-v]
  (fn [cofx [req]]
    (when-not (= :success (sync-state (:db cofx) req))
      (sync-event-fx cofx req))))

;; makes it a little easier to sync a single entity once
(sth/rr rf/reg-event-fx ::sync-entity-once
  [rf/trim-v]
  (fn [cofx [req]]
    (when-not (= :success (sync-state (:db cofx) req))
      (sync-event-fx cofx (sync-entity-req req)))))

;; only sync if there's no active sync
(sth/rr rf/reg-event-fx ::sync-unless-active
  [rf/trim-v]
  (fn [cofx [req]]
    (when-not (= :active (sync-state (:db cofx) req))
      (sync-event-fx cofx req))))

;; only sync entity if there's no active sync
(sth/rr rf/reg-event-fx ::sync-entity-unless-active
  [rf/trim-v]
  (fn [cofx [req]]
    (when-not (= :active (sync-state (:db cofx) req))
      (sync-event-fx cofx (sync-entity-req req)))))

;; The effect handler that actually performs a sync
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

;; TODO rename this to sync-fx-handler :(
(defn sync-fx
  "Returns an effect handler that dispatches a sync event"
  [[method endpoint opts]]
  (fn [_cofx [call-opts params]]
    {:dispatch [::sync [method endpoint (build-opts opts call-opts params)]]}))

;; TODO rename this to sync-once-fx-handler :(
(defn sync-once-fx
  "Returns an effect handler that dispatches a sync event"
  [[method endpoint opts]]
  (fn [_cofx [call-opts params]]
    {:dispatch [::sync-once [method endpoint (build-opts opts call-opts params)]]}))

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

;;---------------
;; response helpers
;;---------------

(defn single-entity
  [ctx]
  (->> (get-in ctx [:resp :response-data])
       (filter #(= :entity (first %)))
       (first)
       (second)
       (vals)
       (first)
       (vals)
       (first)))
