(ns sweet-tooth.frontend.routes
  (:require [integrant.core :as ig]
            [sweet-tooth.frontend.routes.protocol :as strp]
            [sweet-tooth.frontend.routes.reitit]
            [clojure.spec.alpha :as s]))

(s/def ::route-name keyword?)
(s/def ::params map?)
(s/def ::lifecycle map?)

(s/def ::route (s/keys :req-un [::route-name
                                ::params]
                       :opt-un [::lifecycle]))

(s/def ::router (s/keys :req-un [::routes]))

(def frontend-router (atom nil))
(def api-router (atom nil))

(defn path
  [name route-params query-params]
  (strp/path @frontend-router name route-params query-params))

(defmethod ig/init-key ::frontend-router [_ config]
  (reset! frontend-router (strp/router config)))

(defmethod ig/init-key ::api-router [_ config]
  (reset! api-router (strp/router config)))
