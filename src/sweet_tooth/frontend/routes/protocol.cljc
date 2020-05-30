(ns sweet-tooth.frontend.routes.protocol)

(defprotocol Router
  (path
    [this name]
    [this name route-params]
    [this name route-params query-params]
    "generate a path")

  (req-id
    [this name]
    [this name route-params]
    "a req-id is used to distinguish multiple requests to the same
    resource by their params for sync bookkeeping")

  (route
    [this path-or-name]
    [this path-or-name route-params]
    [this path-or-name route-params query-params]
    "Given a path OR a route name, return the route that corresponds"))

(defmulti router :use)
