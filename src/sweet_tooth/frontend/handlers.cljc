(ns sweet-tooth.frontend.handlers
  "Sweet Tooth provides handlers. Rather than registering outright,
  handlers are registered with an internal registry using the `rr`
  function in this namespace, and the `register-handlers` function is
  then used to register the handlers with re-frame. This allows users
  to specify interceptors for Sweet Tooth's handlers.

  TODO: document how you can apply interceptors hierarchically by ns"
  (:require [meta-merge.core :refer [meta-merge]]
            [integrant.core :as ig]))

(def handlers (atom {}))

(defn register-registration
  "Store a re-frame registration in sweet little atom"
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

(defmethod ig/init-key ::register-handlers
  [_ interceptors]
  (register-handlers interceptors))
