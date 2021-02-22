# Changelog

## [0.14.0] in progress

### Added

- More sync interceptors:
  - `sync-form-path`, use form buffer data for params and route params
  - `sync-data-path`, performs get-in on db and merged in to params and
    route-params
- `stcu/move-keys` for smuggling top-level keys into a nested map
- `stnu/route`, `stnu/params`, `stnu/route-name` route helpers

### Changed

- New, simpler validation system. Form flow is now only responsible for
  recording input events; it's not responsible for handling the logic of
  validating and placing errors in the `:errors` key. Forms no longer take a
  `:validate` key. Instead, they take a `:dscr-sub` key which should be a
  subscription. The subscription should be multi-arity; when it receivs a form
  path, it should describe the form as a whole. When it receives form path and
  attr-path, it should describe the attr.
  
  The notion of "description" is a better separation of concerns: the
  subscription is a view of the form, and it can be any function. It's more
  flexible than just validation, because sometimes you want to describe the form
  or attrs beyond just "has error". Sometimes it's "is valid", sometimes it's
  "upload complete", sometimes it's "username available". 
  
  The form `field` component helper has been updated to display descriptions.
  
  A new namespace, `sweet-tooth.frontend.form.describe`, was added with a
  default description sub, `::stored-errors`, which simply return any errors
  stored in a form. It also includes `reg-describe-validation-sub`, which
  creates a subscription that's meant to capture a common pattern of only
  showing errors when a user attempts to submit a form. In the future, this ns
  will include additional helpers, e.g. one that will display error messages for
  an input when it receivs a blur.
- renamed on-submit-handler to submit-fn for brevity, plus updated submit-fn to
  allow for a couple sugared inputs
- update signature of route lifecycle guards (`:can-exit?` etc) to `[db
  existing-route new-route]`
- event handler merging for form components. If you supply a handler that has a
  framework default, like `:on-changed`, the handler default will be passed in
  as the second argument and the input's opts as the third


### Removed

- removed `stnf/*-with-route-params`, which was used to add frontend route
  params to a sync request. that functionality is now possible with `stsf/get
  {:rules #{merge-route-params}}`. The removed handlers weren't compatible with
  the rest of the sync interceptors, like `:once`

### Fixed

- `:select` input component has a default `:format-read` that relaces `nil` with
  `""`
- nav flow lifecycle correctly now only calls `:can-exit?` on existing route and
  `:can-enter?` on new route


## [0.13.3] 2020-11-08

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
- added `stsf/sync-req->sync-event` to avoid awkward composition of generating a
  function and immediately applying it, which was necessary with `stsf/sync-fx`
- added `stsf/sync-req->dispatch`

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
- renamed `stsf/sync-fx` to `stsf/sync-fx-handler`
- renamed `stsf/sync-once-fx` to `stsf/sync-once-fx-handler`

## [0.13.2] 2020-09-06

### Fixed

- Fixed `:$ctx` reference in sync flow success
- checkbox input uses `:checked` instead of `:default-checked` so its controlled


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
