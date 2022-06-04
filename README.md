# init

[![Build Status](https://img.shields.io/github/workflow/status/ferdinand-beyer/init/Main.svg)](https://github.com/ferdinand-beyer/init/actions)
[![cljdoc](https://cljdoc.org/badge/com.fbeyer/init)][cljdoc]
[![Clojars](https://img.shields.io/clojars/v/com.fbeyer/init.svg)][clojars]

_Dependency injection a la carte._

Init is a small Clojure framework for application state management and dependency
injection.  It is heavily inspired by [Integrant][integrant] and similar to
[Component][component], but also draws ideas from Java frameworks like
[Dagger 2][dagger], [Guice][guice], [Spring][spring] and [CDI][cdi].

**STATUS**: Alpha.  API might still change.

## Rationale

Similar to Integrant being a reaction on perceived weaknesses with Component,
Init is a reaction on perceived weaknesses with Integrant.

In Integrant, systems are built from a configuration map that is usually read
from an EDN file.  Then the keys in the configuration have to be mapped to
code, which requires loading namespaces that define multimethods.  This design
has its challenges:

* In order for Integrant to help with namespace loading, the configuration keys
  have to match namespaces in the code.
* Providing multimethod implementations either leads to a lot of repetitive
  "glue code", or couples your application with Integrant.
* The configuration and implementation need to grow in parallel, but because
  they are separated, it is easy to forget one or the other.

In the Java community, there has been a clear transition from file based
configuration (like early Spring's XML configuration) towards annotation-based
configuration, directly in code.

Init aims at providing the same annotation based experience to Clojure
programmers, using Clojure's powerful metadata capabilities, while staying
simple, data-driven, and transparent.

However, one solution does not fit all.  Therefore one of the fundamental goals
of Init is modularity, allowing users to mix and match techniques.

## Installation

Releases are available from [Clojars][clojars].  
See the Clojars page for tool-specific instructions.

## Documentation

* [API Docs][cljdoc]
* [Example project](./examples/todo-app/)

## Motivating example

Defining dependencies via metadata:

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
  {:init/inject [::handler [:get ::config :port]]}
  [handler port]
  (jetty/run-jetty handler {:port port}))

(defn -main []
  (-> (discovery/from-namespaces [*ns*])
      (init/start)))
```

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

[Ferdinand Beyer]: https://fbeyer.com
[MIT License]: https://opensource.org/licenses/MIT
