# Metadata

You can configure components using metadata on vars:

* [`:init/name`](#name)
* [`:init/tags`](#tags)
* [`:init/inject`](#injecting-dependencies)
* [`:init/stop-fn`](#stop-functions), [`:init/stops`](#stop-functions)

If you prefer, check out Init's specs in the `init.specs` namespace.

## Components

Vars can be interpreted as components.  When scanning namespaces, init will
only pick up vars that have at least one supported metadata key.

Vars without metadata can be components when they are either constants or
nullary functions.  Otherwise, init will not know which values to supply as
arguments when starting the component.

### Name

The default component name is the qualified var name, converted to keyword.

```clojure
(defn my-component [])
; => #'user/my-component

(:name (init.meta/component #'my-component))
; => :user/my-component
```

The name can be specified explicitly using the `:init/name` metadata,
and must be a qualified keyword or symbol.

```clojure
(defn my-component {:init/name ::foo} [])
; => #'user/my-component

(:name (init.meta/component #'my-component))
; => :user/foo
```

`:init/name` can also be used to tag vars as components, using the default name:

```clojure
(defn ^:init/name my-component [])
```

### Tags

Additional tags can be specified with the `:init/tags` metadata:

```clojure
(defn start-server
  {:init/tags #{:http/server :app/service}}
  [handler]
  (httpkit/run-server handler))
```

Init also treats [type hints](https://clojure.org/reference/java_interop#typehints)
as tags, so that you can inject components by Java type.

### Injecting dependencies

You can specify selectors for components to inject via the `:init/inject`
metadata key.  In its basic form, this is a vector with one element for each
function argument.

Selectors can be qualified names (keywords or symbols) or collections of
qualified names.  For collections, a component needs to provide _all_ tags in
the collection to be eligible for injection.

In the following example, the `::server` component requires two components.
The first one needs to provide `:ring/handler`, the second both `:server/port`
and `:env/prod`:

```clojure
(defn server
  {:init/inject [:ring/handler [:server/port :env/prod]]}
  [handler port]
  (httpkit/run-server handler {:port port}))
```

Most of the time, dependencies are considered _unique_ and must match exactly
one component in the configuration.  Otherwise, it would be an error.

You can specify that a dependency can take zero or more matching components as
a set, by putting the required tags in a set:

```clojure
(defn router
  {:init/inject [#{:reitit.route/data}]}
  [routes]
  (reitit/router (vec routes)))
```

#### Injecting maps

You can inject multiple dependencies as a map, using tags as keys:
`[:keys val+]`.

This component has two dependencies, `::foo` and `::bar`, and will get them
injected as a map:

```clojure
(defn injecting-keys
  {:init/inject [[:keys ::foo ::bar]]}
  [m]
  (println "Received foo:" (::foo m) "and bar:" (::bar m)))
```

For more advanced selection and full control over the maps's key, use
the map form:

```clojure
(defn injecting-maps
  {:init/inject [{:db [:app/db :profile/prod]}]}
  [m]
  (query (:db m)))
```

This also supports arbitrary nesting.

#### Lookup with `:get`

A typical use case is to define a component that loads some configuration from
a configuration file or the environment into a map, and to require keys from
configuration in a component.

For that, you can use the `:get` form `[:get selector k+]`, taking a component
selector `selector` and one or more keys `k`.  Multiple keys form a path as
understood by `get-in`.

```clojure
(defn load-config
  {:init/name :app/config}
  []
  {:http {:port 8080}})

(defn start-server
  {:init/inject [:ring/handler [:get :app/config :http :port]]}
  [handler port]
  (httpkit/run-server handler {:port port}))
```

#### Call functions with `:apply`

For the case where Init does not provide what you need, you can transform
injected values with Clojure functions using `[:apply f selector*]`:

```clojure
(defn inject-with-apply
  {:init/inject [[:apply str/lower-case ::string-component]]}
  [s]
  (print s))
```

### Advanced injection

For function components, you can instruct Init to combine injected values
with runtime arguments.

You can bind left-most arguments to injected values using a partial
application: `[:partial selector*]`.

```clojure
(defn lookup
  {:init/inject [:partial :app/db]
  [db id]
  (find-entity db id)})
```

At runtime, your `::lookup` component will take one argument, `id`, while
the `db` will be bound to the value provided by the `:app/db` component.

Init can also inject values into collection arguments, merging them with
runtime values using `(into runtime-arg injected)`.  For this to make sense,
the injected value needs to be a collection itself.

There are two variants for this:

* `[:into-first selector]` adds the injected value into the first argument
* `[:into-last selector]` adds the injected value into the last argument


```clojure
(defn ring-handler
  {:init/inject [:into-first {:db :app/db}]}
  [request])

(defn http-request
  {:init/inject [:into-last [:keys :http/client]]}
  [url opts])
```

### Stop functions

If your component needs to perform cleanup task when the system is stopped, you
can supply a stop function.

Stop functions take one argument: the component value to stop.

There are two ways to do so: `:init/stop-fn` defines a function directly on the
component var, and `:init/stops` declares that the var having this metadata is
the stop function for an existing component.

```clojure
(declare stop-server)

(defn start-server
  {:init/stop-fn #'stop-server}
  []
  (server/start))

(defn stop-server [server]
  (server/stop server))
```

You can specify the function as:

* Function, e.g. declared inline or by resolving to a var in the same namespace
* Symbol, resolving to function-valued vars in the same namespace
* Var, having the stop function as value

The `:init/stops` key must be a keyword, symbol or var referencing an existing
component:

```clojure
(defn ^:init/name start-server []
  (server/start))

(defn stop-server
  {:init/stops #'start-server}
  [server]
  (server/stop server))
```

Using vars is preferred for both, as they will survive refactoring.
