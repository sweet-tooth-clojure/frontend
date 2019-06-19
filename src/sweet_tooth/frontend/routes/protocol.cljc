(ns sweet-tooth.frontend.routes.protocol)

(defprotocol Router
  (path
    [this name]
    [this name route-params]
    [this name route-params query-params])
  (req-id
    [this name]
    [this name route-params])
  (route
    [this path]))

(defmulti router :use)
