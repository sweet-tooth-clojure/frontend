(ns sweet-tooth.frontend.nav.handler
  "Adapted from Accountant, https://github.com/venantius/accountant
  Accountant is licensed under the EPL v1.0."
  (:require [clojure.string :as str]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [re-frame.core :as rf]
            [integrant.core :as ig]
            [sweet-tooth.frontend.handlers :as sth]
            [sweet-tooth.frontend.routes.flow :as strf])
  (:import goog.history.Event
           goog.history.Html5History
           goog.Uri))

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
      (let [token (.-token e)]
        (nav-handler token)))))

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
  [history path-exists? reload-same-path?]
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
            current-relative-href (str (.-pathname loc) (.-query loc) (.-hash loc))]
        (when (and (not any-key)
                   (#{"" "_self"} link-target)
                   (= button 0)
                   (= host current-host)
                   (or (not port)
                       (= (str port) (str current-port)))
                   (path-exists? path))
          (when (not= current-relative-href relative-href) ;; do not add duplicate html5 history state
            (rf/dispatch [::update-token relative-href :set title]))
          (.preventDefault e)
          (when reload-same-path?
            (events/dispatchEvent history (Event. path true))))))))

;; TODO add a window.beforeunload handler
(defn init-handler
  "Create and configure HTML5 history navigation.

  nav-handler: a fn of one argument, a path. Called when we've decided
  to navigate to another page. You'll want to make your app draw the
  new page here.

  path-exists?: a fn of one argument, a path. Return truthy if this path is handled by the SPA"
  [{:keys [match-route reload-same-path?] :as config}]  
  (let [history      (new-history)
        nav-handler  (fn [path] (rf/dispatch [::dispatch-route path]))
        path-exists? (comp :route-name match-route)]
    {:nav-handler  nav-handler
     :path-exists? path-exists?
     :match-route  match-route
     :history      history
     :listeners    {:document-click (prevent-reload-on-known-path history path-exists? reload-same-path?)
                    :navigate       (dispatch-on-navigate history nav-handler)}}))

(defmethod ig/init-key ::handler
  [_ config]
  (init-handler config))

(defn halt-handler!
  "Teardown HTML5 history navigation.

  Undoes all of the stateful changes, including unlistening to events, that are setup as part of
  the call to `configure-navigation!`."
  [handler]
  (.dispose (:history handler))
  (doseq [key (vals (:listeners handler))]
    (events/unlistenByKey key)))

(defmethod ig/halt-key! ::handler
  [_ handler]
  (halt-handler! handler))

(defn map->params [query]
  (let [params (map #(name %) (keys query))
        values (vals query)
        pairs (partition 2 (interleave params values))]
    (str/join "&" (map #(str/join "=" %) pairs))))

(sth/rr rf/reg-event-fx ::navigate
  []
  (fn [cofx [route query]]
    (let [{:keys [nav-handler history]} (get-in cofx [:db :sweet-tooth/system ::handler])]
      (if nav-handler
        (let [token (.getToken history)
              old-route (first (str/split token "?"))
              query-string (map->params (reduce-kv (fn [valid k v]
                                                     (if v
                                                       (assoc valid k v)
                                                       valid)) {} query))
              with-params (if (empty? query-string)
                            route
                            (str route "?" query-string))]
          (if (= old-route route)
            {:dispatch [::update-token with-params :replace]}
            {:dispatch [::update-token with-params :set]}))
        (js/console.error "can't navigate until handler is initialized")))))

;; interceptor takes URL
;; interceptor converts URL to route
;; interceptor checks that change is allowable
;; interceptor places results as coeffect
(def route
  {:id     ::route
   :before (fn [context]
             (let [match-route (get-in context [:coeffects :db :sweet-tooth/system ::handler :match-route])
                   path        (get-in context [:coeffects :event 1])]
               (assoc-in context [:coeffects ::route] (match-route path))))
   :after  identity})

;; ------
;; dispatch route
;; ------

(sth/rr rf/reg-event-fx ::dispatch-route
  [route]
  (fn [cofx _]
    {::dispatch-route (::route cofx)}))

(sth/rr rf/reg-fx ::dispatch-route
  (fn [route]
    (strf/dispatch-route route)))

;; ------
;; dispatch current
;; ------

(defn dispatch-current-effect
  [cofx]
  {::dispatch-current (get-in cofx [:db :sweet-tooth/system ::handler :match-route])})

(sth/rr rf/reg-event-fx ::dispatch-current
  []
  (fn [cofx _] (dispatch-current-effect cofx)))

(sth/rr rf/reg-fx ::dispatch-current
  (fn [match-route]
    (let [path  (-> js/window .-location .-pathname)
          query (-> js/window .-location .-search)
          hash  (-> js/window .-location .-hash)]
      (strf/dispatch-route (match-route (str path query hash))))))

;; ------
;; update token
;; ------

;; TODO do I need to ensure that dispatch-route finished before update-token?
(sth/rr rf/reg-event-fx ::update-token
  [route]
  (fn [cofx [_ relative-href op title]]
    {::dispatch-route (::route cofx)
     ::update-token   {:history       (get-in cofx [:db :sweet-tooth/system ::handler :history])
                       :relative-href relative-href
                       :title         title
                       :op            op}}))

(sth/rr rf/reg-fx ::update-token
  (fn [{:keys [op history relative-href title]}]
    (if (= op :replace)
      (. history (replaceToken relative-href title))
      (. history (setToken relative-href title)))))
