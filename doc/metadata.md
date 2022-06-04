# Metadata

Configuration using metadata on vars.  All metadata uses keyword keys with
the `init` namespace.

## Components

Vars can be interpreted as components.  When scanning namespaces, init will
only pick up vars that have at least one supported metadata key.

Vars without metadata can be components when they are either constants or
nullary functions.  Otherwise, init will not know which values to supply as
arguments when starting the component.

### Name

The default component name is the qualified var name, coerced into a keyword.

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

### Injection Points

You can specify selectors for components to inject via the `:init/inject`
metadata key.  In its basic form, this is a vector with one element for each
function argument.

Selectors can be qualified names (keywords or symbols) or collections of
qualified names.  In the following example, the `::server` component requires
two components.  The first one needs to provide `:ring/handler`, the second
both `:server/port` and `:env/prod`:

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

* `{:k val}`
* `[:keys val+]`

#### Transforming injected values

* `[:get val k+]`
* `[:apply f val*]`

#### Advanced injection

Function components.

As a convenience, allows injecting into runtime arguments.

* `:partial`: Partial application of the function, dependencies are bound when
  the component is provided
* `:into-first`, `:into-last`: Wrap the function and inject dependencies into
  the first/last argument:

```clojure
(defn ring-handler
  {:init/inject [:into-first {:db :app/db}]}
  [request])

(defn http-request
  {:init/inject [:into-last [:keys :http/client]]}
  [url opts])
```

### Stop functions

* `:init/stop-fn` can be a function taking the component instance and perform
  clean-up.

Stop functions can referenced via:
* Functions, e.g. declared inline or by resolving to a var in the same namespace
* Symbols, resolving to function-valued vars
* Vars containing functions implementing the handler (preferred)

Handlers can also be specified reversely by metadata on a var.  `:init/stops`
must be a keyword, symbol or var referencing an existing component, and registers
the var as a stop function for the referenced component:

```clojure
(def start-server []
  (server/start))

(def stop-server
  {:init/stops #'start-server}
  [server]
  (server/stop server))
```
