(ns sweet-tooth.frontend.sync.flow
  "Sync provides a layer of indirection between requests to read/write
  data and the mechanism for handling the request. It also provides a
  common interface across those mechanisms."
  (:require [re-frame.core :refer [reg-fx reg-event-fx dispatch]]
            [ajax.core :refer [GET PUT POST DELETE]]
            [taoensso.timbre :as timbre]
            [sweet-tooth.frontend.core :as stc]
            [sweet-tooth.frontend.core.flow :as stcf]
            [integrant.core :as ig]))

(defn req-path
  [[method resource opts]]
  [method resource (select-keys opts [:params])])

(defn new-request
  [db req]
  (-> db
      (assoc-in [::requests (req-path req)] {:state :active})
      (update ::active-request-count (fnil inc 0))))

(defn sync-event-fx
  [cofx req]
  (-> cofx
      (dissoc :event)
      (update :db new-request req)
      (merge {::sync (assoc cofx ::req req)})))

(defn sync-dispatch-fn
  [cofx]
  (get-in cofx [:db
                :sweet-tooth.frontend/config
                ::sync
                :sync-dispatch]))

;; TODO write a schema describing the config that can be sent here
;; TODO possibly add some timeout effect here to clean up sync
(defmethod ig/init-key :sweet-tooth.frontend.sync.flow/sync
  [_ {:keys [interceptors] :as opts}]
  (reg-event-fx ::sync
    interceptors
    (fn [cofx [_ & req]]
      (sync-event-fx cofx req)))

  (reg-fx ::sync
    (fn [cofx]
      ((sync-dispatch-fn cofx) cofx)))

  opts)
