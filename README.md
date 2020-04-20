# Sweet Tooth Frontend

Just as an operating system provides resources like the filesystem to
address needs that are not specific to any particular application, The
Sweet Tooth Frontend framework aims to address many common needs that
single page apps have in common.

The library is a layer on top of
[re-frame](https://github.com/day8/re-frame/), providing handlers,
subscriptions, components, utilities and conventions to give you a
sane, extensible starting point to deal with:

* Forms
  * storing their data in the app db
  * validation
  * the submission lifecycle
  * components that "wirte things up" so you don't have to.
* API calls
  * sharing routes between frontend and backend
  * keeping track of active calls (e.g. to show activity indicators)
  * call lifecycle
* URL-based navigation with HTML history
* Pagination

## Forms

Sweet Tooth eliminates much of the boilerplate for creating forms.

### with-form

- the form "name"
  - used for storing form state
  - used for sync
- inputs
- fields
- submission
  - syncing
  - lifecycle / state that gets updated
  - what gets submitted - extra data

### Validation

### success

### fail

## Sync

## Structuring Data

## Pagination

## URLs and routing

## Flows (organization)

## Dev

## Demo

## Running tests

Watch with

```
lein doo
```

Run once with

```
lein doo node test once
```
