# Init
Dependency injection a la carte.

Init is a small Clojure framework for application state management and dependency
injection.  It is heavily inspired by [Integrant][integrant] and similar to
[Component][component], but also draws ideas from Java frameworks like
[Dagger 2][dagger], [Guice][guice], [Spring][spring] and [CDI][cdi].

## Rationale

Similar to Integrant being a reaction on perceived weaknesses with Component,
Init is a reaction on perceived weaknesses with Integrant.

In Integrant, systems are built from a configuration map that is usually read
from an EDN file.  Then the keys in the configuration have to be mapped to
code, which involves loading namespaces and providing multimethods.  This
separation seemed brittle to me:

* In order for Integrant to help with namespace loading, the configuration keys
  have to match namespaces in the code.
* Providing multimethod implementations either leads to a lot of repetitive
  "glue code", or couples your application with Integrant.

In the Java community, there was a clear trend to move from configuration files
like early Spring's XML configuration towards annotation-based configuration in
code.

Init aims at providing the same for Clojure, using Clojure's powerful metadata
capabilities, while staying simple, data-driven, and transparent.

One solution does not fit all.  Therefore one of the fundamental goals of Init
is to break the task of application initialization apart, and allowing users to
mix and match.

## Installation

Releases are available from [Clojars][clojars].  See the Clojars page for
tool-specific instructions.

## Usage

### Dependency Inversion

Consider a typical Clojure web application (example borrowed from James Reeves'
[Enter Integrant][enter-integrant] talk):

```clojure
(def database
  (db/connect "jdbc:sqlite:"))

(defn handler [request]
  (resp/response (query-status database)))

(defn -main []
  (jetty/run-jetty handler {:port 8080}))
```

Code like this is difficult to test and reason about.  The `database` is
_global state_, and the fact that the `handler` requires the `database` is
_implicit_ and _opaque_.  In order to test `handler`, you will probably need
to mock the database with something like `with-redefs`.  As the application
grows, keeping track of these implicit dependencies will prove a challenge.

With _dependency inversion_, we would rewrite this code like this:

```clojure
(defn read-config []
  (edn/read-string (slurp "config.edn")))

(defn database [uri]
  (db/connect uri))

(defn handler [database]
  (fn [request]
    (resp/response (query-status database))))

(defn start-server [port]
  (jetty/run-jetty handler {:port port}))

(defn -main []
  (let [config  (load-config)
        db      (database (:db-uri config))
        handler (handler database)]
    (start-server handler (:port config))))
```

All vars are now functions, taking their dependencies as arguments.
However, the code got more complex, and manually keeping track of
initialization order might get tiresome as the application grows.

### Metadata configuration

With Init, the example above can be written like this:

```clojure
(defn read-config
  {:init/name ::config}
  []
  (edn/read-string (slurp "config.edn")))

(defn database
  {:init/inject [[:get ::config :db-uri]]}
  [uri]
  (db/connect uri))

(defn handler
  {:init/inject [:into-first {:db ::database}]}
  [{:keys [db] :as request}]
  (resp/response (query-status db)))

(defn start-server
  {:init/inject [::handler {:port [:get ::config :port]}]}
  [port]
  (jetty/run-jetty handler {:port port}))

(defn -main []
  (-> (discovery/from-namespaces [*ns*])
      (init/start)))
```

We use metadata to _describe_ a function's dependencies, then let Init figure
out how to wire components together.

### A la carte

While loading configuration through metadata is preferred, it is completely
optional.

Init divides application initialization into multiple independent tasks:

* **Discovery** is the process of obtaining a _configuration_ of _components_.
  This can be achieved by different means, e.g.:
  * Programmatically
  * Reading a configuration file
  * Discovering components through metadata annotations
* **Dependency Resolution** builds a _dependency graph_ from a _configuration_,
  validating it on the fly.  At this stage, we can ensure that every component's
  dependencies can be fulfilled, unambiguously.
* **Initialization** starts a _system_ from a _dependency graph_.
* **Shutdown** eventually stops the _system_.

Init provides a _specification_ of concepts and programming models, as well as
a reference implementation.

## Documentation

* [API Docs][cljdoc]


## License

Distributed under the [MIT License].  
Copyright &copy; 2022 [Ferdinand Beyer]


[cdi]: https://www.cdi-spec.org/
[cljdoc]: https://cljdoc.org/jump/release/com.fbeyer/init
[clojars]: https://clojars.org/com.fbeyer/init
[component]: https://github.com/stuartsierra/component
[dagger]: https://dagger.dev/
[guice]: https://github.com/google/guice
[integrant]: https://github.com/weavejester/integrant
[mount]: https://github.com/tolitius/mount
[spring]: https://spring.io/

[enter-integrant]: https://skillsmatter.com/skillscasts/9820-enter-integrant

[Ferdinand Beyer]: https://fbeyer.com
[MIT License]: https://opensource.org/licenses/MIT
