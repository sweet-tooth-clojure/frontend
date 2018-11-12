(ns demo.location-dispatch
  (:require [re-frame.core :as rf]
            [accountant.core :as acc]
            [clojure.string :as str]
            [bide.core :as bide]

            [demo.routes :as routes]
            [demo.components.home :as h]
            
            [sweet-tooth.frontend.core.utils :as stcu]
            [sweet-tooth.frontend.routes.flow :as strf]
            [sweet-tooth.frontend.routes.utils :as stru]
            [sweet-tooth.frontend.form.flow :as stff]))


(defmulti dispatch-route (fn [handler params] handler))

(defmethod dispatch-route :home
  [handler params]
  (rf/dispatch [::strf/load handler {:main [h/component]} params]))
