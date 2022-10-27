# Introduction

Init is a small and flexible framework for application initialization and
dependency injection.

## Configuration as data

A configuration defines the components of your system and how they depend
on each other.  A configuration is an ordinary Clojure map, and components
can be defined as ordinary maps as well:

```clojure
(def config
  {:http/port    {:name     :http/port
                  :start-fn (constantly 8080)}
   :ring/handler {:name     :ring/handler
                  :start-fn #'my-app.handler.handle-request}
   :http/server  {:name     :http/server
                  :deps     [:ring/handler :http/port]
                  :start-fn #'my-app.http.start-server}})
```

Since it is just data, you can easily build your configuration from other
formats, inspect and transform your configuration, transfer it via the wire,
etc.

## Vars are components

Init encourages you to write loosely coupled components as plain old Clojure
vars.  Applying [dependency inversion](./dependency-inversion.md), you
write simple functions that take required values as arguments:

```clojure
(defn start-server [handler port]
  (httpkit/run-server handler {:port port}))
```

## Configuration via metadata

Configure your components with metadata on the var, right where you write the
code:

```clojure
(defn start-server
  {:init/inject [:ring/handler :http/port]}
  [handler port]
  (httpkit/run-server handler {:port port}))
```

Metadata is just data and does not require your code to depend on
init's namespaces.  Your code stays idiomatic Clojure, you can test it without
special mechanisms, and can use it completely without init if you want.

You can annotate library code to make it easy to use with init, without forcing
a framework on your users.

Init's metadata also serves as documentation.

## Declarative injection mini-language

Write less "glue code" by declaring how your dependencies should be injected
into your component.

For example, consider a Ring handler that requires a database connection.
Using dependency inversion, you would wrap your handler in a constructor
function:

```clojure
(defn handle-request
  {:init/inject [:app/db]}
  [database]
  (fn [request]
    (resp/response (query database))))
```

Alternatively, you could use partial application to bind the `database`
argument:

```clojure
(defn handle-request
  {:init/inject [:partial :app/db]}
  [database request]
  (resp/response (query database)))
```

This instructs init to partially apply your function on start, so that your
component will be a valid one-argument Ring handler.

Finally, you could decide to take your dependencies in the `request` itself,
as if it would be provided by Ring middleware.  Init has special support for
that as well:

```clojure
(defn handle-request
  {:init/inject [:into-first {:db :app/db}]}
  [request]
  (resp/response (query (:db request))))
```

## Classpath scanning

Init can automatically scan your classpath for Clojure namespaces that
define components:

```clojure
;; Find all namespaces with prefix "my-app" on the classpath, and build
;; a config map:
(defn config (init.discovery/scan ['my-app]))
```

You can do so at runtime or at compile time:

```clojure
(defn config (init.discovery/static-scan ['my-app]))
```

## Component selection via tags

Organise your components by tags, and declare injections using tags.  This
allows you to decouple your components, and to find all components providing
a certain functionality in the system:

```clojure
(defn database-healthy?
  {:init/tags   #{:health/checker}
   :init/inject [:app/db]}
  [db]
  (fn [] (heartbeat-query db)))

(defn message-broker-healthy?
  {:init/tags   #{:health/checker}
   :init/inject [:events/broker]}
  [broker]
  (fn [] (ping broker)))

(defn health-endpoint
  {:init/inject [:partial #{:health/checker}]}
  [checkers request]
  (if (every? (fn [healthy?] (healthy?)) checkers))
    (resp/ok {:status :healthy})
    (resp/service-unavailable {:status :unhealthy}))
```

## Tag hierarchy

In addition to tagging, you can use Clojure's default hierarchy and `derive`
to declare is-a relationships, as Init uses `isa?` to find matching
components.

```clojure
(defn start-server []
  (http/run-server))

(derive ::start-server :http/server)
```

Similarly, you can also use Java classes as tags, and find all components that
provide a certain class or one of its subclasses.

## Start and stop components in dependency order

Once you have a configuration, leave it to Init to start all your components in
the correct order and build a system map.

When your application shuts down, Init will stop your components in reverse
dependency order, making sure all resources are released properly.

## Small footprint

Init is a fairly small library and has very few dependencies.  At the moment,
it only requires [`com.stuartsierra/dependency`][dependency-lib].

Other dependency are optional and users will need to provide them when they
want to use their functionality:

* [`com.fbeyer/autoload`][autoload] for service-loader style discovery

## Modular design

Init is [modular in design](design.md):

* Configuration is just data, and `init.config` is agnostic to how the
  configuration is built.
* Configuring vars via metadata is only one way to obtain a configuration.
  If you don't want to, you don't need to use it.
* You can use Init's dependency graph and system lifecycle, or run your own.
* You could just as well use Init's discovery mechanisms to build a config map
  for Integrant.

[autoload]: https://github.com/ferdinand-beyer/autoload
[dependency-lib]: https://github.com/stuartsierra/dependency
