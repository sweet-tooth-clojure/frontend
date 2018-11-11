(ns demo.sync.dispatch.local
  (:require [re-frame.core :as rf]
            [ajax.core :refer [GET PUT POST DELETE]]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [integrant.core :as ig]

            [demo.sync.dispatch.local.data :as data]))

(defmulti success-resp (fn [[method res opts]] [method res]))

(defmethod success-resp [:get :init]
  [req]
  (data/populate {:post [[5]]})
  [{:entity (merge (data/ent-gen :post)
                   (data/ent-gen :topic)
                   (data/ent-gen :user)
                   (data/ent-gen :topic-category))}])

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
