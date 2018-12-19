## Sync

Syncing consists of submitting a request and handling a response. The
sync lifecycle is to: 

- Dispatch a request -> transitions to `:active`
- Listen for response
- Handle response -> transitions to `:success` or `:fail`

Why do I call this _syncing_ and not AJAX, XHR, or something else?
_Sync_ is meant to capture the more general purpose of making an XHR
request, along with related concerns that aren't inherently provided
by XHR. A sync is tracked in the app-db so that the current state of
its lifecycle is accessible. The most common use case is to show an
activity indicator in the UI when a request is active. Sync
accomplishes this by giving each sync request its own address in the
app db.

Syncing is a process that's independent of the underlying
mechanism. The basic idea is that you want the state in Place A
propagated to Place B. This propagation can take time, and it can
result in one of an enumerated set of possible outcomes: one kind of
success, many kinds of failure. XHR is one mechanism by which this can
take place.

The sync abstraction exposes a consistent way to work with the
higher-level concerns in syncing data. It gives you tools for handling
success and failure consistently. It also handles updating where the
sync request is in its lifecycle.

By treating syncing as an abstraction, we make it possible to
implement it using different dispatching mechanisms than XHR. For
example, you could create a fully-local dispatcher that simulates
remote requests by generating data for the response and using timeouts
to simulate request latency.

Separates the data describing a request from actually performing the
request. This allows success and failure handlers to be constructed
that reference the original request, which can be useful - for
example, the request data is used to derive an address for that sync,
and that address is used to make the lifecycle state accessible. The
response handlers are thus able to update the lifecycle, because they
have access to the sync's address.

### Request Structure

A request is a vector of

```clojure
[method resource-name opts]
```

* `method` is one of `#{:get :query :put :update :post :create :delete}`
* `resource-name` will correspond to a route
* `opts` can include
  * `:on-success`
  * `:on-fail`
  * `:params`

### AJAX dispatching, adapters

You'll notice that the request structure does not include a URI. AJAX
requests (obviously) need a URI. The request adapter takes the request
as an argument, and adds a URI to the last element of the request,
`opts`.

`sweet-tooth.frontend.sync.dispatch.bide` includes the default request
adapter that comes bundled with Sweet Tooth. It uses routes to update
the request by adding `:uri` to `opts`.

## Routes

* Backend routes
* URL routes
