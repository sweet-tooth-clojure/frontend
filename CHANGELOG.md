# Changelog

## [0.13.3] IN PROGRESS

### Added

- `sweet-tooth.frontend.nav.utils/routed-entity-form`, helper that returns form
  for routed entity
- `:sweet-tooth.frontend.core.flow/remove-entity` handler
- broke out form sync subs to
  `sweet-tooth.frontend.form.components/form-sync-subs`
- `sweet-tooth.frontend.sync.flow/sync-subs`
- `sweet-tooth.frontend.sync.flow/remove-req`
- `sweet-tooth.frontend.sync.flow/remove-reqs` - filter many reqs by key and value
- `::stsf/remove-reqs` handler that calls above
- `::stsf/remove-reqs-by-route-and-method` creates filters for above to remove
  reqs that match sets of methods and route names

### Changed

- Made sync behavior more modular with interceptors. Can add `:rules` to sync
  options, including:
  - `:once` only sync when there is no existing sync with a `:success` status
  - `:merge-route-params` merges in route params
  - `:when-not-active` only sync if there is not an existing, active sync
    (prevents form double submit for example)
- Added `:entity-path` key to sync options. When specified, will look up entity
  at path and use it for `:route-params` and `:params` of a request.
- Made "reitit could not generate path" warning less noisy
- allow `nil` sync responses


## [0.13.2] 2020-09-06

### Fixed

- Fixed `:$ctx` reference in sync flow success


## [0.13.1] 2020-07-03

### Added

- `sweet-tooth.frontend.routes/route`: get a route from the frontend
  router

### Changed

- When nav flow doesn't find a path's route, it attempts to use a
  `::stnf/not-found` route
- `sweet-tooth.frontend.routes.protocol/route` can now take a route name
- `sweet-tooth.frontend.sync.dispatch.ajax/request-methods` now
  includes all supported request methods
- sync requests now have a default 503 handler specifically
- project name from `sweet-tooth/sweet-tooth-frontend` to `sweet-tooth/frontend`

## [0.13.0] 2020-05-24

### Changed

- Can now specify `:method` as a sync opt, further allowing the
  request signature (name) to diverge from the sync calls
- Cleaned up form macro, getting rid of unnecessary duplication
- added `:default-on` to sync opts to eliminate the need to always
  have to specify the default handlers whenever you want to have
  custom sync handlers. If you need to treat default handlers in some
  custom way you can do something like:

  ```clojure
  {:default-on {:success :skip}
   :on         {:success [[:first :event]
                          [::stsf/default-sync-success :$ctx]
                          [:next :event]]}
  ```
  
  But you're probably better off just writing a named handler.

## [0.12.7] 2020-05-10

### Added

- `sweet-tooth.frontend.sync.flow/sync-entity-req`: use like
  `(sync-entity-req :put :comment {:id 1 :content
  \"comment\"})`. Handles common case of just wanting to sync an
  entity by associating the entity into the `:route-params` and
  `:params` sync options. 
- `:sweet-tooth.frontend.sync.flow/sync-entity` handler
- `:sweet-tooth.frontend.sync.flow/sync-entity-once` handler
- `:sweet-tooth.frontend.sync.flow/sync-unless-active` handler, now
  used by `sweet-tooth.frontend.form.flow/submit-form` to prevent
  double form submission
- `:sweet-tooth.frontend.nav.flow/navigate-route` handler, allows you
  to navigate using route data instead of a path string

### Fixed

- Input component options weren't getting applied correctly
