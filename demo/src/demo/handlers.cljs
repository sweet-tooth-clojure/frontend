(ns demo.handlers
  (:require [ajax.core :refer [GET PUT]]
            [re-frame.core :as rf]
            [accountant.core :as acc]
            [sweet-tooth.frontend.paths :as paths]
            [sweet-tooth.frontend.core.flow :as stcf]
            [sweet-tooth.frontend.form.flow :as stff]
            [sweet-tooth.frontend.remote.flow :as strf]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [sweet-tooth.frontend.pagination.flow :as stpf]
            [sweet-tooth.frontend.core.utils :as stcu]))

(def window-clicked-path [:global-handlers :window-clicked])

(rf/reg-event-fx :window-clicked
  (fn [{:keys [db]} _]
    {:db             (assoc-in db window-clicked-path {})
     :dispatch-later (vals (get-in db window-clicked-path))}))

(rf/reg-event-fx ::init-success
  [rf/trim-v]
  (fn [cofx [[init-ent-db]]]
    (-> (update cofx :db merge init-ent-db)
        (assoc :nav/dispatch-current true))))

(rf/reg-event-fx :init
  [rf/trim-v]
  (fn [cofx [config]]
    (stsf/sync-event-fx {:db {:global-handlers             {:window-clicked {}}
                              :sweet-tooth.frontend/config config}}
                        [:get :init {:on-success [::init-success]}])))

;;--------------------
;; nav
;;--------------------

(rf/reg-fx :nav/dispatch-current
  (fn [_]
    (acc/dispatch-current!)))
