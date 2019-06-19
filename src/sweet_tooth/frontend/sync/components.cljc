(ns sweet-tooth.frontend.sync.components
  (:require [re-frame.core :as rf]
            [sweet-tooth.frontend.sync.flow :as stsf]
            [sweet-tooth.frontend.nav.flow :as stnf]))

(defn loadable-component
  [sync-state-sub loading-component empty-component loaded-component]
  (let [sync-state @(rf/subscribe sync-state-sub)
        nav-state  @(rf/subscribe [::stnf/nav-state])]
    (cond (or (= :loading nav-state) (= :active sync-state))
          ^{:key "loading"} loading-component

          (not loaded-component)
          ^{:key "empty"} empty-component

          :else
          ^{:key "loaded"} loaded-component)))
