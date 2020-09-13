(ns sweet-tooth.frontend.nav.flow
  "Adapted from Accountant, https://github.com/venantius/accountant
  Accountant is licensed under the EPL v1.0."
  (:require [goog.events :as events]
            [goog.events.EventType]
            [re-frame.core :as rf]
            [integrant.core :as ig]
            [taoensso.timbre :as log]
            [medley.core :as medley]
            [sweet-tooth.frontend.nav.accountant :as accountant]
            [sweet-tooth.frontend.paths :as paths]
            [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.core.compose :as stcc]
            [sweet-tooth.frontend.handlers :as sth]
            [sweet-tooth.frontend.nav.ui.flow :as stnuf]
            [sweet-tooth.frontend.nav.utils :as stnu]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [sweet-tooth.frontend.routes :as stfr]
            [sweet-tooth.frontend.routes.protocol :as strp]))

(defn- handle-unloading
  []
  (let [listener (fn [e] (rf/dispatch-sync [::before-unload e]))]
    (.addEventListener js/window "beforeunload" listener)
    listener))

(defn init-handler
  "Configures accountant, window unloading, keeps track of event
  handlers for integrant teardown"
  [{:keys [router dispatch-route-handler reload-same-path? check-can-unload? global-lifecycle] :as _config}]
  (let [history      (accountant/new-history)
        nav-handler  (fn [path] (rf/dispatch [dispatch-route-handler path]))
        update-token (fn [relative-href title] (rf/dispatch [::update-token relative-href :set title]))
        path-exists? #(strp/route router %)]
    {:router           router
     :history          history
     :global-lifecycle global-lifecycle
     :listeners        (cond-> {:document-click (accountant/prevent-reload-on-known-path history
                                                                                         path-exists?
                                                                                         reload-same-path?
                                                                                         update-token)
                                :navigate       (accountant/dispatch-on-navigate history nav-handler)}
                         check-can-unload? (assoc :before-unload (handle-unloading)))}))

(defmethod ig/init-key ::handler
  [_ config]
  (init-handler config))

(defn halt-handler!
  "Teardown HTML5 history navigation.

  Undoes all of the stateful changes, including unlistening to events,
  that are setup when init'd"
  [handler]
  (.dispose (:history handler))
  (doseq [key (vals (select-keys (:listeners handler) [:document-click :navigate]))]
    (events/unlistenByKey key))
  (when-let [before-unload (get-in handler [:listeners :before-unload])]
    (.removeEventListener js/window "beforeunload" before-unload)))

(defmethod ig/halt-key! ::handler
  [_ handler]
  (halt-handler! handler))

(defn- navigate-handler
  [{:keys [db] :as cofx} [path query]]
  (let [{:keys [history]} (paths/get-path db :system ::handler)
        token             (.getToken history)
        query-string      (u/params-to-str (reduce-kv (fn [valid k v]
                                                        (if v
                                                          (assoc valid k v)
                                                          valid))
                                                      {}
                                                      query))
        with-params       (if (empty? query-string)
                            path
                            (str path "?" query-string))]
    (if (= token with-params)
      {:dispatch [::update-token with-params :replace]}
      {:dispatch [::update-token with-params :set]})))

;; Used for synthetic navigation events
(sth/rr rf/reg-event-fx ::navigate
  [rf/trim-v]
  navigate-handler)

(sth/rr rf/reg-event-fx ::navigate-route
  [rf/trim-v]
  (fn [{:keys [db] :as cofx} [route route-params query-params]]
    (let [router (paths/get-path db :system ::handler :router)]
      (when-let [path (strp/path router route route-params query-params)]
        (navigate-handler cofx [path])))))

;; ------
;; Route change handlers
;; ------
(defn can-change-route?
  [db scope existing-route-lifecycle new-route-lifecycle]
  ;; are we changing the entire route or just the params?
  (let [route-change-checks (-> (merge existing-route-lifecycle new-route-lifecycle)
                                (select-keys (case scope
                                               :route  [:can-change-params? :can-exit? :can-enter?]
                                               :params [:can-change-params?])))
        check-failures      (medley/filter-vals (fn [lifecycle-fn]
                                                  (and lifecycle-fn (not (lifecycle-fn db))))
                                                route-change-checks)]
    (or (empty? check-failures)
        (log/debug ::prevented-route-change {:check-failures (set (keys check-failures))}))))

(def process-route-change
  "Intercepor that interprets new route, adding a ::route-change coeffect"
  {:id     ::process-route-change
   :before (fn [{{:keys [db event]} :coeffects
                 :as                ctx}]
             (let [global-lifecycle (paths/get-path db :system ::handler :global-lifecycle)
                   router           (paths/get-path db :system ::handler :router)
                   path             (get event 1)
                   new-route        (or (strp/route router path)
                                        (strp/route router ::not-found))
                   existing-route   (paths/get-path db :nav :route)
                   scope            (if (= (:route-name new-route) (:route-name existing-route))
                                      :params
                                      :route)

                   new-route-lifecycle      (:lifecycle new-route)
                   existing-route-lifecycle (when existing-route (:lifecycle existing-route))]
               (assoc-in ctx [:coeffects ::route-change]
                         {:can-change-route? (can-change-route? db scope existing-route-lifecycle new-route-lifecycle)
                          :scope             scope
                          :old-route         existing-route
                          :new-route         new-route
                          :global-lifecycle  global-lifecycle})))
   :after identity})

;; ------
;; dispatch route
;; ------
(defn compose-route-lifecycle
  [cofx lifecycle hook-names fx]
  (let [{:keys [new-route old-route global-lifecycle]} (::route-change cofx)]
    (->> hook-names
         (map (fn [hook-name]
                (when-let [hook (or (and (contains? lifecycle hook-name)
                                         (hook-name lifecycle))
                                    (hook-name global-lifecycle))]
                  (if (fn? hook)
                    (hook cofx new-route old-route)
                    hook))))
         (filter identity)
         (into fx))))

(defn route-effects
  "Handles all route lifecycle effects"
  [cofx]
  (let [{:keys [scope old-route new-route]} (::route-change cofx)]
    (cond->> []
      (= scope :route) (compose-route-lifecycle cofx (:lifecycle old-route) [:before-exit :exit :after-exit])
      (= scope :route) (compose-route-lifecycle cofx (:lifecycle new-route) [:before-enter :enter :after-enter])
      true             (compose-route-lifecycle cofx (:lifecycle new-route) [:before-param-change :param-change :after-param-change])
      true             (into [{:dispatch-later [{:ms 0 :dispatch [::nav-loaded]}]}]))))

(defn change-route-fx
  "Composes all effects returned by lifecycle methods"
  ([cofx _] (change-route-fx cofx))
  ([{:keys [db] :as cofx}]
   (let [{:keys [can-change-route?] :as route-change-cofx} (::route-change cofx)]
     (when can-change-route?
       (let [db (-> db
                    (assoc-in (paths/full-path :nav :route) (:new-route route-change-cofx))
                    (assoc-in (paths/full-path :nav :state) :loading))]
         (let [updated-cofx (assoc cofx :db db)]
           (stcc/compose-fx (into [{:db db}] (route-effects updated-cofx)))))))))

;; Default handler for new routes
(sth/rr rf/reg-event-fx ::dispatch-route
  [process-route-change]
  change-route-fx)

(sth/rr rf/reg-event-db ::nav-loaded
  [rf/trim-v]
  (fn [db _]
    (assoc-in db (paths/full-path :nav :state) :loaded)))

;; ------
;; dispatch current
;; ------

(def add-current-path
  {:id     ::add-current-path
   :before (fn [ctx]
             (let [path  (-> js/window .-location .-pathname)
                   query (-> js/window .-location .-search)
                   hash  (-> js/window .-location .-hash)]
               (assoc-in ctx [:coeffects :event 1] (str path query hash))))
   :after  identity})

(sth/rr rf/reg-event-fx ::dispatch-current
  [add-current-path process-route-change]
  change-route-fx)

;; force the param change and enter lifecycle methods of the current
;; route to run again.
(sth/rr rf/reg-event-fx ::perform-current-lifecycle
  []
  (fn [{:keys [db] :as cofx} _]
    (let [current-route (get-in db (paths/full-path :nav :route))]
      (change-route-fx (assoc cofx ::route-change {:can-change-route? true
                                                   :scope             :route
                                                   :new-route         current-route
                                                   :global-lifecycle  (paths/get-path db :system ::handler :global-lifecycle)})))))

;; ------
;; update token
;; ------

;; TODO figure out when this is actually used...
(sth/rr rf/reg-event-fx ::update-token
  [process-route-change]
  (fn [{:keys [db] :as cofx} [_ relative-href op title]]
    (when-let [fx (change-route-fx cofx)]
      (assoc fx ::update-token {:history       (paths/get-path db :system ::handler :history)
                                :relative-href relative-href
                                :title         title
                                :op            op}))))

(sth/rr rf/reg-fx ::update-token
  (fn [{:keys [op history relative-href title]}]
    (reset! accountant/app-updated-token? true)
    (if (= op :replace)
      (. history (replaceToken relative-href title))
      (. history (setToken relative-href title)))))

;; ------
;; check can unload
;; ------

(sth/rr rf/reg-event-fx ::before-unload
  []
  (fn [{:keys [db] :as cofx} [_ before-unload-event]]
    (let [existing-route                          (paths/get-path db :nav :route)
          {:keys [can-unload?]
           :or   {can-unload? (constantly true)}} (when existing-route (:lifecycle existing-route))]
      (when-not (can-unload? db)
        {::cancel-unload before-unload-event}))))

(rf/reg-fx ::cancel-unload
  (fn [before-unload-event]
    (.preventDefault before-unload-event)
    (set! (.-returnValue before-unload-event) "")))


;; ------
;; nav flow components
;; ------

(def default-global-lifecycle
  {:before-exit         nil
   :after-exit          nil
   :before-enter        (constantly [::stnuf/clear :route])
   :after-enter         nil
   :before-param-change (constantly [::stnuf/clear :params])
   :after-param-change  nil})

(defmethod ig/init-key ::global-lifecycle [_ lifecycle]
  lifecycle)

;; ------
;; subscriptions
;; ------

(defn nav
  [db]
  (get db (paths/prefix :nav)))

(rf/reg-sub ::nav
  (fn [db _]
    (nav db)))

(rf/reg-sub ::route
  :<- [::nav]
  (fn [nav _] (:route nav)))

(rf/reg-sub ::nav-state
  :<- [::nav]
  (fn [nav _] (:state nav)))

(rf/reg-sub ::params
  :<- [::nav]
  (fn [nav _] (:params (:route nav))))

(rf/reg-sub ::routed-component
  :<- [::route]
  (fn [route [_ path]]
    (get-in route (u/flatv :components path))))

(rf/reg-sub ::route-name
  :<- [::route]
  (fn [route _] (:route-name route)))

(rf/reg-sub ::routed-entity
  (fn [db [_ entity-key param]]
    (stnu/routed-entity db entity-key param)))

;; uses routed path params to get sync state
(rf/reg-sub ::route-sync-state
  (fn [db [_ path-prefix]]
    (stsf/sync-state db (conj path-prefix (-> db nav :route :params)))))

;; ------
;; sync dispatching with routes
;; ------

;; TODO remove these
(defn- method-sync-fx
  [method]
  (fn [{:keys [db] :as cofx} [route-name opts]]
    (stsf/sync-event-fx cofx [method route-name (merge {:route-params (get-in (nav db) [:route :params])}
                                                       opts)])))

(sth/rr rf/reg-event-fx ::get-with-route-params
  [rf/trim-v]
  (method-sync-fx :get))

(sth/rr rf/reg-event-fx ::put-with-route-params
  [rf/trim-v]
  (method-sync-fx :put))

(sth/rr rf/reg-event-fx ::post-with-route-params
  [rf/trim-v]
  (method-sync-fx :post))

(sth/rr rf/reg-event-fx ::delete-with-route-params
  [rf/trim-v]
  (method-sync-fx :delete))

;; ------
;; form interactions
;; ------

(sth/rr rf/reg-event-db ::initialize-form-with-routed-entity
  [rf/trim-v]
  (fn [db [form-path entity-key param-key form-opts]]
    (stnu/initialize-form-with-routed-entity db form-path entity-key param-key form-opts)))

(rf/reg-event-fx ::navigate-to-synced-entity
  [rf/trim-v]
  (fn [_ [route-name ctx]]
    {:dispatch [::navigate (stfr/path route-name (stsf/single-entity ctx))]}))
