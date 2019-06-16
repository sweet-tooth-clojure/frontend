(ns sweet-tooth.frontend.nav.flow
  "Adapted from Accountant, https://github.com/venantius/accountant
  Accountant is licensed under the EPL v1.0."
  (:require [clojure.string :as str]
            [goog.events :as events]
            [goog.events.EventType]
            [goog.history.EventType :as EventType]
            [re-frame.core :as rf]
            [integrant.core :as ig]
            [ajax.url :as url]
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

(def app-updated-token? (atom false))

(defn- transformer-create-url
  [token path-prefix location]
  (str path-prefix token))

(defn- transformer-retrieve-token
  [path-prefix location]
  (str (.-pathname location) (.-search location) (.-hash location)))

(defn new-history
  []
  (let [transformer (goog.history.Html5History.TokenTransformer.)]
    (set! (.. transformer -retrieveToken) transformer-retrieve-token)
    (set! (.. transformer -createUrl) transformer-create-url)
    (doto (Html5History. js/window transformer)
      (.setUseFragment false)
      (.setPathPrefix "")
      (.setEnabled true))))

(defn- dispatch-on-navigate
  [history nav-handler]
  (events/listen
    history
    EventType/NAVIGATE
    (fn [e]
      (if-not @app-updated-token?
        (let [token (.-token e)]
          (nav-handler token))
        (reset! app-updated-token? false)))))

(defn- get-href-attribute
  "Given a DOM node, if it is an element node, return its href attribute.
  Otherwise, return nil."
  [node]
  (when (and node (= (.-nodeType node) js/Node.ELEMENT_NODE))
    (.getAttribute node "href")))

(defn- find-href-node
  "Given a DOM element that may or may not be a link, traverse up the DOM tree
  to see if any of its parents are links. If so, return the href content, if
  it does not have an explicit `data-trigger` attribute to signify a non-navigational
  link element."
  [e]
  (let [href (get-href-attribute e)
        attrs (.-attributes e)
        navigation-link? (and href attrs (-> attrs (aget "data-trigger") not))]
    (if navigation-link?
      e
      (when-let [parent (.-parentNode e)]
        (recur parent)))))

(defn- uri->query [uri]
  (let [query (.getQuery uri)]
    (when-not (empty? query)
      (str "?" query))))

(defn- uri->fragment [uri]
  (let [fragment (.getFragment uri)]
    (when-not (empty? fragment)
      (str "#" fragment))))

(defn- prevent-reload-on-known-path
  "Create a click handler that blocks page reloads for known routes"
  [history router reload-same-path?]
  (events/listen
    js/document
    "click"
    (fn [e]
      (let [target (.-target e)
            button (.-button e)
            meta-key (.-metaKey e)
            alt-key (.-altKey e)
            ctrl-key (.-ctrlKey e)
            shift-key (.-shiftKey e)
            any-key (or meta-key alt-key ctrl-key shift-key)
            href-node (find-href-node target)
            href (when href-node (.-href href-node))
            link-target (when href-node (.-target href-node))
            uri (.parse Uri href)
            path (.getPath uri)
            query (uri->query uri)
            fragment (uri->fragment uri)
            relative-href (str path query fragment)
            title (.-title target)
            host (.getDomain uri)
            port (.getPort uri)
            current-host js/window.location.hostname
            current-port js/window.location.port
            loc js/window.location
            current-relative-href (str (.-pathname loc)
                                       (or (.-query loc) (.-search loc))
                                       (.-hash loc))]
        (when (and (not any-key)
                   (#{"" "_self"} link-target)
                   (= button 0)
                   (= host current-host)
                   (or (not port)
                       (= (str port) (str current-port)))
                   (strp/route router path))
          (when (not= current-relative-href relative-href) ;; do not add duplicate html5 history state
            (rf/dispatch [::update-token relative-href :set title]))
          (.preventDefault e)
          (.stopPropagation e)
          (when reload-same-path?
            (events/dispatchEvent history (Event. path true))))))))

(defn- handle-unloading
  []
  (let [listener (fn [e] (rf/dispatch-sync [::before-unload e]))]
    (.addEventListener js/window "beforeunload" listener)
    listener))

(defn init-handler
  "Create and configure HTML5 history navigation.

  nav-handler: a fn of one argument, a path. Called when we've decided
  to navigate to another page. You'll want to make your app draw the
  new page here.

  path-exists?: a fn of one argument, a path. Return truthy if this path is handled by the SPA"
  [{:keys [router dispatch-route-handler reload-same-path? check-can-unload?] :as config}]  
  (let [history     (new-history)
        nav-handler (fn [path] (rf/dispatch [dispatch-route-handler path]))]
    {:nav-handler nav-handler
     :router      router
     :history     history
     :listeners   (cond-> {:document-click (prevent-reload-on-known-path history router reload-same-path?)
                           :navigate       (dispatch-on-navigate history nav-handler)}
                    check-can-unload? (assoc :before-unload (handle-unloading)))}))

(defmethod ig/init-key ::handler
  [_ config]
  (init-handler config))

(defn halt-handler!
  "Teardown HTML5 history navigation.

  Undoes all of the stateful changes, including unlistening to events,
  that are setup as part of the call to `configure-navigation!`."
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
  (fn [cofx [route query]]
    (let [{:keys [nav-handler history]} (get-in cofx [:db :sweet-tooth/system ::handler])
          
          token        (.getToken history)
          query-string (url/params-to-str (reduce-kv (fn [valid k v]
                                                       (if v
                                                         (assoc valid k v)
                                                         valid)) {} query))
          with-params  (if (empty? query-string)
                         route
                         (str route "?" query-string))]
      (if (= token with-params)
        {:dispatch [::update-token with-params :replace]}
        {:dispatch [::update-token with-params :set]}))))

;; ------
;; Route change handlers
;; ------

(defmulti route-lifecycle :route-name)

(defn can-change-route?
  [db scope {:keys [can-exit? can-change-params?]
             :or   {can-exit?          (constantly true)
                    can-change-params? (constantly true)}}]
  ;; are we changing the entire route or just the params?
  (if (= scope :route)
    (and (can-change-params? db) (can-exit? db))
    (can-change-params? db)))

;; Intercepor that interprets new route, adding a ::route coeffect
(def process-new-route
  {:id     ::process-new-route
   :before (fn [ctx]
             (let [{:keys [db] :as cofx} (:coeffects ctx)
                   router                (get-in db [:sweet-tooth/system ::handler :router])
                   path                  (get-in cofx [:event 1])
                   new-route             (strp/route router path)
                   existing-route        (get-in db (paths/full-path :nav :route))
                   scope                 (if (= (:route-name new-route) (:route-name existing-route))
                                           :params
                                           :route)
                   
                   new-route-lifecycle      (:lifecycle new-route)
                   existing-route-lifecycle (when existing-route (:lifecycle existing-route))]
               (assoc-in ctx [:coeffects ::route]
                         {:can-change-route? (can-change-route? db scope existing-route-lifecycle)
                          :lifecycle         (merge (select-keys existing-route-lifecycle [:exit])
                                                    (select-keys new-route-lifecycle [:enter :param-change]))
                          :scope             scope
                          :components        (:components new-route)
                          :route             new-route})))
   :after identity})

;; ------
;; dispatch route
;; ------

(defn new-route-fx
  ([cofx _] (new-route-fx cofx))
  ([{:keys [db] :as cofx}]
   (let [{:keys [can-change-route?] :as route-cofx} (::route cofx)]
     (when can-change-route?
       (let [db (-> (assoc-in db (paths/full-path :nav) (select-keys route-cofx [:route :components]))
                    (assoc-in (paths/full-path :nav :state) :loading))]
         {:db               db
          ::route-lifecycle (assoc cofx :db db)})))))

;; Default handler for new routes
(sth/rr rf/reg-event-fx ::dispatch-route
  [process-new-route]
  new-route-fx)

(sth/rr rf/reg-fx ::route-lifecycle
  (fn [cofx]
    (let [{:keys [lifecycle scope] :as route} (::route cofx)
          {:keys [exit param-change enter]}   lifecycle
          route                               (:route route)]
      (when (= scope :route)
        (when exit (exit cofx route))
        ;; TODO make this configurable: it should be possible for the
        ;; ui name space to opt in to nav flow lifecycle hooks, as
        ;; opposed to nav flow having to know about UI
        (rf/dispatch [::stnuf/clear :route])
        (when enter (enter cofx route)))
      (when param-change
        ;; TODO make this configurable
        (rf/dispatch [::stnuf/clear :params])
        (param-change cofx route)))
    (rf/dispatch [::nav-loaded])))

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
  [add-current-path process-new-route]
  new-route-fx)

;; force the param change and enter lifecycle methods of the current
;; route to run again.
(sth/rr rf/reg-event-fx ::perform-current-lifecycle
  []
  (fn [{:keys [db] :as cofx} _]
    {::route-lifecycle {:db        db
                        :scope     :route
                        :lifecycle (-> db
                                       (get-in (paths/full-path :nav :route))
                                       route-lifecycle
                                       (select-keys [:enter :param-change]))}}))

;; ------
;; update token
;; ------

(sth/rr rf/reg-event-fx ::update-token
  [process-new-route]
  (fn [cofx [_ relative-href op title]]
    (when-let [fx (new-route-fx cofx)]
      (assoc fx ::update-token {:history       (get-in cofx [:db :sweet-tooth/system ::handler :history])
                                :relative-href relative-href
                                :title         title
                                :op            op}))))

(sth/rr rf/reg-fx ::update-token
  (fn [{:keys [op history relative-href title]}]
    (reset! app-updated-token? true)
    (if (= op :replace)
      (. history (replaceToken relative-href title))
      (. history (setToken relative-href title)))))

;; ------
;; check can unload
;; ------

(sth/rr rf/reg-event-fx ::before-unload
  []
  (fn [{:keys [db] :as cofx} [_ before-unload-event]]
    (let [existing-route                          (get-in db (paths/full-path :nav :route))
          {:keys [can-unload?]
           :or   {can-unload? (constantly true)}} (when existing-route (:lifecycle existing-route))]
      (when-not (can-unload? db)
        {::cancel-unload before-unload-event}))))

(rf/reg-fx ::cancel-unload
  (fn [before-unload-event]
    (.preventDefault before-unload-event)
    (set! (.-returnValue before-unload-event) "")))


;; ------
;; subscriptions
;; ------

(defn nav
  [db]
  (get db (paths/prefix :nav)))

(rf/reg-sub ::nav
  (fn [db _]
    (nav db)))

(rf/reg-sub ::nav-state
  :<- [::nav]
  (fn [nav _] (:state nav)))

(rf/reg-sub ::params
  :<- [::nav]
  (fn [nav _] (:params (:route nav))))

(rf/reg-sub ::routed-component
  :<- [::nav]
  (fn [nav [_ path]]
    (get-in nav (u/flatv :components path))))

(rf/reg-sub ::route-name
  :<- [::nav]
  (fn [nav _] (get-in nav [:route :route-name])))

(rf/reg-sub ::routed-entity
  (fn [db [entity-key param]]
    (stnu/routed-entity db entity-key param)))

;; uses value of param-key to form request signature
(rf/reg-sub ::route-sync-state
  (fn [db [_ path-prefix param-key]]
    (stsf/sync-state db (conj path-prefix (-> db nav :route :params param-key)))))
