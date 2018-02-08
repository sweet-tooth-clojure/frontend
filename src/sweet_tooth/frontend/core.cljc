(ns sweet-tooth.frontend.core
  (:require [meta-merge.core :refer [meta-merge]]))

(def config
  (atom {:paths {:form   :form
                 :page   :page
                 :entity :entity
                 :nav    :nav}}))

(def handlers (atom {}))

(defn register-registration
  [{:keys [id] :as registration}]
  (let [id-ns (symbol (namespace id))]
    (swap! handlers update id-ns (fn [registrations]
                                   (conj (or registrations []) registration)))))

(defn rr
  "Register a re-frame registration."
  ([reg-fn id handler-fn]
   (rr reg-fn id [] handler-fn))
  ([reg-fn id interceptors handler-fn]
   (register-registration {:reg-fn reg-fn
                           :id id
                           :interceptors interceptors
                           :handler-fn handler-fn})))

(defn register-handler
  [registration id-ns interceptors]
  (let [configured-registration (meta-merge registration
                                            {:interceptors (get interceptors id-ns)}
                                            {:interceptors (get interceptors (:id registration))})
        [reg-fn & reg-args] (->> ((juxt :reg-fn :id :interceptors :handler-fn)
                                  (cond-> configured-registration
                                    (empty? (:interceptors registration)) (dissoc :interceptors)))
                                 (filter identity))]
    (apply reg-fn reg-args)))

(defn register-handlers
  [& [interceptors]]
  (doseq [[id-ns registrations] @handlers]
    (doseq [registration registrations]
      (register-handler registration id-ns interceptors))))
