(ns sweet-tooth.frontend.sync.dispatch.bide
  (:require [bide.core :as bide]
            [integrant.core :as ig]))

(defmethod ig/init-key ::req-adapter
  [_ routes]
  (let [router (bide/router routes)]
    (fn [[method res opts]]
      [method res (assoc opts :uri (bide/resolve router res))])))
