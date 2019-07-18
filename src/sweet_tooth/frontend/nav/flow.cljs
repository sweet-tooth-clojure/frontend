(ns sweet-tooth.frontend.nav.flow
  "Adapted from Accountant, https://github.com/venantius/accountant
  Accountant is licensed under the EPL v1.0."
  (:require [clojure.string :as str]
            [goog.events :as events]
            [goog.events.EventType]
            [goog.history.EventType :as EventType]
            [re-frame.core :as rf]
            [integrant.core :as ig]
            [sweet-tooth.frontend.nav.accountant :as accountant]
            [sweet-tooth.frontend.paths :as paths]
            [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.handlers :as sth]
            [sweet-tooth.frontend.nav.ui.flow :as stnuf]
            [sweet-tooth.frontend.nav.utils :as stnu]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [sweet-tooth.frontend.routes.protocol :as strp])
  (:import goog.history.Event
           goog.history.Html5History
           goog.Uri))

(defn- handle-unloading
  []
  (let [listener (fn [e] (rf/dispatch-sync [::before-unload e]))]
    (.addEventListener js/window "beforeunload" listener)
    listener))

(defn init-handler
  "Configures accountant, window unloading, keeps track of event
  handlers for integrant teardown"
  [{:keys [router dispatch-route-handler reload-same-path? check-can-unload? global-lifecycle] :as config}]  
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

;; Used for synthetic navigation events
(sth/rr rf/reg-event-fx ::navigate
  [rf/trim-v]
  (fn [{:keys [db] :as cofx} [route query]]
    (let [{:keys [history]} (paths/get-path db :system ::handler)
          token             (.getToken history)
          query-string      (u/params-to-str (reduce-kv (fn [valid k v]
                                                          (if v
                                                            (assoc valid k v)
                                                            valid))
                                                        {}
                                                        query))
          with-params       (if (empty? query-string)
                              route
                              (str route "?" query-string))]
      (if (= token with-params)
        {:dispatch [::update-token with-params :replace]}
        {:dispatch [::update-token with-params :set]}))))

;; ------
;; Route change handlers
;; ------
(defn can-change-route?
  [db scope {:keys [can-exit? can-change-params?]
             :or   {can-exit?          (constantly true)
                    can-change-params? (constantly true)}}]
  ;; are we changing the entire route or just the params?
  (if (= scope :route)
    (and (can-change-params? db) (can-exit? db))
    (can-change-params? db)))

(def process-route-change
  "Intercepor that interprets new route, adding a ::route-change coeffect"
  {:id     ::process-route-change
   :before (fn [{{:keys [db event]} :coeffects
                 :as                ctx}]
             (let [global-lifecycle (paths/get-path db :system ::handler :global-lifecycle)
                   router           (paths/get-path db :system ::handler :router)
                   path             (get event 1)
                   new-route        (strp/route router path)
                   existing-route   (paths/get-path db :nav :route)
                   scope            (if (= (:route-name new-route) (:route-name existing-route))
                                      :params
                                      :route)
                   
                   new-route-lifecycle      (:lifecycle new-route)
                   existing-route-lifecycle (when existing-route (:lifecycle existing-route))]
               (assoc-in ctx [:coeffects ::route-change]
                         {:can-change-route? (can-change-route? db scope existing-route-lifecycle)
                          :scope             scope
                          :old-route         existing-route
                          :new-route         new-route
                          :global-lifecycle  global-lifecycle})))
   :after identity})

;; ------
;; dispatch route
;; ------

(defn change-route-fx
  "Generates :db effect with with the new route and produces
  the ::change-route effect"
  ([cofx _] (change-route-fx cofx))
  ([{:keys [db] :as cofx}]
   (let [{:keys [can-change-route?] :as route-change-cofx} (::route-change cofx)]
     (when can-change-route?
       (let [db (-> db
                    (assoc-in (paths/full-path :nav :route) (:new-route route-change-cofx))
                    (assoc-in (paths/full-path :nav :state) :loading))]
         {:db            db
          ::change-route (assoc cofx :db db)})))))

;; Default handler for new routes
(sth/rr rf/reg-event-fx ::dispatch-route
  [process-route-change]
  change-route-fx)

(defn do-route-lifecycle-hooks
  [cofx lifecycle hook-names]
  (let [{:keys [new-route old-route global-lifecycle]} (::route-change cofx)]
    (doseq [hook-name hook-names]
      (when-let [hook (or (and (contains? lifecycle hook-name)
                               (hook-name lifecycle))
                          (hook-name global-lifecycle))]
        (hook cofx new-route old-route)))))

;; TODO look into defining the flow with data, like
;; [[:before :route] [:before :params] [:change :route] [:change :params] [:after :params] [:after :route]]
(defn change-route
  [cofx]
  (let [{:keys [scope old-route new-route]}      (::route-change cofx)
        {:keys [exit before-exit after-exit]}    (:lifecycle old-route)
        {:keys [param-change before-param-change after-param-change
                enter before-enter after-enter]} (:lifecycle new-route)]
    
    (when (= scope :route)
      (do-route-lifecycle-hooks cofx (:lifecycle old-route)
                                [:before-exit :exit :after-exit])
      (do-route-lifecycle-hooks cofx (:lifecycle new-route)
                                [:before-enter :enter :after-enter]))

    (do-route-lifecycle-hooks cofx (:lifecycle new-route)
                              [:before-param-change :param-change :after-param-change]))
  
  (rf/dispatch [::queue-nav-loaded]))

(sth/rr rf/reg-fx ::change-route change-route)

(sth/rr rf/reg-event-fx ::queue-nav-loaded
  [rf/trim-v]
  (constantly {:dispatch-later [{:ms 0 :dispatch [::nav-loaded]}]}))

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
      {::route-lifecycle {:db     db
                          ::route {:scope     :route
                                   :lifecycle (-> current-route
                                                  :lifecycle
                                                  (select-keys [:enter :param-change]))
                                   :route     current-route}}})))

;; ------
;; update token
;; ------

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
   :before-enter        #(rf/dispatch [::stnuf/clear :route])
   :after-enter         nil
   :before-param-change #(rf/dispatch [::stnuf/clear :params]) 
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
  (fn [db [entity-key param]]
    (stnu/routed-entity db entity-key param)))

;; uses routed path params to get sync state
(rf/reg-sub ::route-sync-state
  (fn [db [_ path-prefix]]
    (stsf/sync-state db (conj path-prefix (-> db nav :route :params)))))
