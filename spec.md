# Init Spec

## Definitions

### Component

Metadata describing a named object that is a building block of the software
system.

### Configuration

A collection of unique components.

### Tag

Identifies components.  Every component has a name, which is a tag, and can
have additional tags.  Tags can have is-a relationships, so that they form
graphs.

### Selector

Criteria to select components in a configuration.  A selector consists of one
or more tags.  A components matches a selector if it provides all tags in the
selector.

### Dependency Graph

A data structure derived from a configuration, by resolving component
dependencies.

### (Component) Instance

A concrete instance of a component; the result of initialising the component.

### System

A map of component instances, obtained from the configuration with the help of
a dependency graph.

## Programmatic configuration

Init provides an API to create configurations programmatically.  On top of
that, there are more convenient ways.

## Metadata configuration

Configuration using metadata on vars.  All metadata uses keyword keys with
the `init` namespace.

### Components / providers

Vars can be interpreted as components and providers.

* Public vars without `:arglists` metadata, i.e. values
* Public vars with `:arglists` metadata that contains `[]`, i.e. nullary
  functions
* Vars tagged with `:init/name`, including private vars and functions
  taking arguments (injection points)

TODO: Functions taking arguments need `:init/inject`
TODO: Discovery (only tagged) vs. supported when added explicitly (e.g. referenced)

#### Name

The default component name is the qualified var name, coerced into a keyword.

The name can be specified explicitly using the `:init/name` metadata,
and must be a qualified keyword.

#### Tags

Additional tags can be specified with the `:init/provides` metadata, which must be
a sequence of qualified keywords.

#### Injection Points

Only for function-valued vars.

Specified with the `:init/inject` metadata key.

When specified in the var metadata, must be a vector corresponding to the
function arguments.  When specified on the argument symbols, must be a single
injection point description (see below).

TODO: Does argument metadata annotation make sense?

#### Provider

TODO: Name? Is that really a provider?

Derived from the var's value.  Functions are provider functions taking
arguments as described by the injection points and returning the component
instance.

Non-function values are considered as singleton instances, and a provider
function taking no arguments and returning this singleton is generated
automatically.

##### Advanced injection

As a convenience, allows injecting into runtime arguments.

* `:partial`: Partial application of the function, dependencies are bound when
  the component is provided
* `:into-first`, `:into-last`: Wrap the function and inject dependencies into
  the first/last argument:

```clojure
(defn ring-handler
  {:init/inject [:into-first :app/db]}
  [request])

(defn http-request
  {:init/inject [:into-last :http/client]}
  [url opts])
```

#### Instance lifecycle handlers

* `:init/disposer` can be a function taking the component instance and perform
  clean-up.

Disposers can be:
* Functions, e.g. declared inline or by resolving to a var in the same namespace
* Symbols, resolving to function-valued vars
* Vars containing functions implementing the handler (preferred)

Handlers can also be specified reversely by metadata on a var.  `:init/disposes`
must be a keyword, symbol or var referencing an existing component, and registers
the var as a halt handler for the referenced component:

```clojure
(def start-server []
  (server/start))

(def stop-server
  {:init/disposes #'start-server}
  [server]
  (server/stop server))
```

### Bindings

TODO

Metadata on components:

```clojure
(defm my-module
  {:init/bind {:some/tag :resolve/this}}
  [])
```

### Injection Points

Defined within `:init/inject`

Unique values:
* `:some/tag`
* `[:some/tag :another/tag]`
* `[:unique :some/tag :another/tag]`

Sets:
* `#{:some/tag}`
* `#{:some/tag :another/tag}`
* `[:set :some/tag :another/tag]`

Maps:
* `[:map :some/tag]`
* `{:some/tag :some/tag}`
* `{:some/tag #{:supports/nesting}}`
* `{:key {:can :be/renamed}}`

## Discovery

Discovers annotated vars automatically in namespaces.  For that, namespaces
need to be loaded, e.g. using `require`.

### Loading namespaces

Namespaces can be loaded manually, or automatically by examining the classpath.

### Config expansion

Start with some components and bindings, automatically `require` and `resolve`
required components.

Especially useful for binding configurations.

#### Classpath scanning

Finds namespaces on the Java classpath and loads them.

#### Service loader

Similar to `java.util.ServiceLoader`, searches the classpath for special files
`META-INF/services/init.namespaces`, and loads all namespaces found this way.

Compatible to `ServiceLoader`, so that the files can be merged properly when
generating uberjars by appending them.  This should be done automatically by
uberjar tooling.
