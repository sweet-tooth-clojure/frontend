# Changelog

# [Unreleased]

### Added

- `sweet-tooth.frontend.sync.flow/sync-entity-req`: use like
  `(sync-entity-req :put :comment {:id 1 :content
  \"comment\"})`. Handles common case of just wanting to sync an
  entity by associating the entity into the `:route-params` and
  `:params` sync options. 
- `:sweet-tooth.frontend.sync.flow/sync-entity` handler
- `:sweet-tooth.frontend.sync.flow/sync-entity-once` handler
- `:sweet-tooth.frontend.nav.flow/navigate-route` handler, allows you
  to navigate using route data instead of a path string
