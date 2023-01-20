# init

[![Build Status](https://img.shields.io/github/actions/workflow/status/ferdinand-beyer/init/main.yml?branch=main)](https://github.com/ferdinand-beyer/init/actions)
[![cljdoc](https://cljdoc.org/badge/com.fbeyer/init)][cljdoc]
[![Clojars](https://img.shields.io/clojars/v/com.fbeyer/init.svg)][clojars]

Init is a small Clojure framework for **application initialization** and
**dependency injection**.  It is heavily inspired by [Integrant][integrant]
and similar to [Component][component], but also draws ideas from popular
Java projects like [Dagger 2][dagger], [Guice][guice], [Spring][spring]
and [CDI][cdi].

* [Configuration as data](./doc/README.md#configuration-as-data)
* [Vars are components](./doc/README.md#vars-are-components)
* Configuration via [metadata](./doc/README.md#configuration-via-metadata)
* [Declarative injection specification](./doc/README.md#declarative-injection-mini-language)
* Runtime and compile-time [classpath scanning](./doc/README.md#classpath-scanning)
* Component selection via [tags](./doc/README.md#component-selection-via-tags)
* [Tag hierarchy](./doc/README.md#tag-hierarchy)
* [Start and stop components](./doc/README.md#start-and-stop-components-in-dependency-order)
  in dependency order
* [Small footprint](./doc/README.md#small-footprint)
* [Modular design](./doc/design.md), pick what you need

**Status**: Alpha.  The concepts should be pretty stable, but API details might
still change.

## Rationale

I developed Init because I was unhappy with available solutions in Clojure.

* [Component][component] relies on records and protocols, and has some
  constraints on what can have dependencies.
* [Mount][mount] encourages global state with hidden dependencies, and does not
  support dependency _inversion_ at all.
* [Integrant][integrant] separates configuration from implementation, which
  sounds like a good idea at first, but adds complexity in practice.

Init borrows many ideas from Integrant, such as configuration as data, keys
that support hierarchy, and system maps, while offering an alternative to derive
configuration from code via metadata.

In the Java community, there has been a clear transition from file based
configuration (like early Spring's XML configuration) towards annotation-based
configuration, directly in code.  Init suggests that this has proven useful,
and offers a similar programming experience to Clojure programmers.

## Usage

Init allows you to define components and their dependencies using Metadata on
vars, and provides a mini-language to customize injected values:

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
      (init/start)
      (init/stop-on-shutdown)))
```

While [configuration via metadata](./doc/metadata.md) is preferred and
distinguishes Init from other solutions, it is completely optional.
Init has a [modular design](./doc/design.md), and allows you to mix and
match different approaches for configuration, discovery and component
lifecycle.

Have a look at [Init's design](./doc/design.md) to find out more.

## Installation

Releases are available from [Clojars][clojars].

deps.edn:

```
com.fbeyer/init {:mvn/version "0.2.90"}
```

Leiningen/Boot:

```
[com.fbeyer/init "0.2.90"]
```

## Documentation

* [Articles and API Docs][cljdoc] are hosted on **cljdoc**
* [Example projects](./examples/)
* [Changelog](./CHANGELOG.md)
* You can also browse the [`doc/`](./doc/) folder

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
