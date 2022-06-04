# Discovery

Init provides multiple flavors of building [configurations](./concepts.md#configuration).

## Namespaces

You can create a config map from one or more namespaces with annotated vars:

```clojure
(require '[my-project.components])
(require '[init.discovery :as discovery])

(def config (discovery/from-namespaces [*ns* (the-ns ('my-project.components)]))
```

Instead of enumerating namespaces manually, you can also specify all namespaces
with a given prefix:

```clojure
(def config (discovery/from-namespaces (discovery/find-namespaces ['my.project])))
```

## Binding

Hand-pick vars to use as components:

```clojure
(def config (discovery/bind {:app/config  #'config/load-config
                             :http/server #'web/start-server}))
```

These vars do not need to be annotated if:

* They have non-function values, i.e. are defined with `def`
* They are nullary (zero argument) functions

Functions that take arguments will need `:init/inject` metadata, so that init
can determine which dependencies to supply as arguments when starting the
component.

## Classpath scanning

Finds namespaces on the classpath matching prefixes:

```clojure
(def config (discovery/scan '[my-app my-team.lib]))
```

This requires [`clojure.tools.namespace`][tools-ns] on the classpath and will
only detect namespaces that are available as source files.

### Static scanning

There is a utility to scan the classpath at compile time:

```clojure
(def config (discovery/static-scan '[my-app my-team.lib]))
```

This way, you will have no runtime dependencies on `tools.namespace` and it will
also work with ahead-of-time compiled namespaces.

## Service Loader

Init supports registration of namespaces via a mechanism similar to
`java.util.ServiceLoader`, using the same file format and location:
It will look for files named `META-INF/services/init.namespaces` on the
classpath.

Example `init.namespaces` file, note the empty lines and comment:

```properties
my-company.config
my-company.server

# Ring handlers
my-company.handlers.api
my-company.handlers.ui
```

To load all of these namespaces:

```clojure
(def config (discovery/services))
```

This requires [`com.fbeyer.autoload`][autoload] on the classpath.

## Mix and match

Most discovery functions accept an existing `config` as first argument, so that
you can freely combine them:

```clojure
(def config (-> (discovery/scan ['my-app.handlers])
                (discovery/bind {:http/server #'server/run-server}
                (discovery/from-namespace [*ns*]))))
```

Since configurations are just maps, you can easily transform and filter them:

```clojure
(require '[init.component :as component])

(def config (->> (discovery/scan ['my-app])
                 (filter #(component/provides? (val %) :profile/production)
                 (into {}))))
```

[autoload]: https://github.com/ferdinand-beyer/autoload
[tools-ns]: https://github.com/clojure/tools.namespace
