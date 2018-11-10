(ns demo.sync.dispatch.local
  (:require [re-frame.core :as rf]
            [ajax.core :refer [GET PUT POST DELETE]]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [integrant.core :as ig]))

(defn success-resp
  [req]
  [{:x {:y {1 :a}}}])

(defn fail-resp
  [req]
  [])

(defn sync-fn
  [sync-config]
  (fn [{:keys [::stsf/req]}]
    (let [[method _ {:keys [uri on-success on-fail fail?] :as opts}] req

          success (stsf/sync-success-handler req on-success)
          fail    (stsf/sync-success-handler req on-fail)]

      (js/setTimeout
        (fn []
          (if fail?
            (fail (fail-resp req))
            (success (success-resp req))))
        (get sync-config :delay 0)))))

(defmethod ig/init-key ::sync
  [_ sync-config]
  (sync-fn sync-config))
