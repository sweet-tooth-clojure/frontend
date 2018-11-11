(ns demo.routes
  (:require [bide.core :as bide]))

(def routes
  (bide/router [["/" :home]
                ["/init" :init]]))
