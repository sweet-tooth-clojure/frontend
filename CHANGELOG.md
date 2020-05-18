# Changelog

# [0.12.8] WIP

### Changed

- Can now specify `:method` as a sync opt, further allowing the
  request signature (name) to diverge from the sync calls
- Cleaned up form macro, getting rid of unnecessary duplication

# [0.12.7] 2020-05-10

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
