(ns sweet-tooth.frontend.js-event-handlers.flow
  "Treat handler registration as an external service, interact with it
  via re-frame effects"
  (:require [re-frame.core :as rf]
            [sweet-tooth.frontend.handlers :as sth]
            [goog.events :as events]
            [integrant.core :as ig]))

(def handlers (atom {}))

(sth/rr rf/reg-fx ::register
  (fn [[identifier hs]]
    (swap! handlers update identifier
           #(when-not %
              (doall (map (fn [[target event-type handler-fn & [capture?]]]
                            (goog.events/listen target
                                                event-type
                                                handler-fn
                                                (boolean capture?)))
                          hs))))))

(sth/rr rf/reg-fx ::unregister
  (fn [identifier]
    (doseq [k (get @handlers identifier)]
      (events/unlistenByKey k))
    (swap! handlers dissoc identifier)))

(defmethod ig/init-key ::handlers [_ {:keys [handlers-atom]}]
  (or handlers-atom handlers))

(defmethod ig/halt-key! ::handlers [_ handlers]
  (doseq [key (apply concat (vals @handlers))]
    (events/unlistenByKey key))
  (reset! handlers {}))
