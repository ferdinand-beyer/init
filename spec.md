# Init Spec

## Definitions

### Component

Metadata describing a named object that is a building block of the software
system.

### Tag

Identifies components.  Every component has a name, which is a tag, and can
have additional tags.  Tags can have is-a relationships, so that they form
graphs.

### (Component) Instance

Concrete object.

### Provider

A function returning component instances.

### Binding

An alias for a component, describes which concrete component shall be injected
when an abstract tag is requested.  This is a kind of provider: Provides the
abstraction by returning the concrete instance.

### Injection Point

Describes references to components to be injected into another component, and
the the shape of the injected value.

### Module

Group of providers and bindings.

### Configuration

Set of components, providers, and modules describing a dependency graph.

Also known as _registry_.

### System

Instance of a configuration.

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
* Vars tagged with `:init/component`, including private vars and functions
  taking arguments (injection points)

#### Name

The default component name is the qualified var name, coerced into a keyword.

The name can be specified explicitly using the `:init/component` metadata,
and must be a qualified keyword.

#### Tags

Additional tags can be specified with the `:init/tags` metadata, which must be
a sequence of qualified keywords.

TODO: Should this be `:init/provides`?

#### Injection Points

Only for function-valued vars.

Specified with the `:init/inject` metadata key or its specialisations
(`:init/inject-partial`, `:init/inject-into`).

When specified in the var metadata, must be a vector corresponding to the
function arguments.  When specified on the argument symbols, must be a single
injection point description (see below).

#### Provider

Derived from the var's value.  Functions are provider functions taking
arguments as described by the injection points and returning the component
instance.

Non-function values are considered as singleton instances, and a provider
function taking no arguments and returning this singleton is generated
automatically.

##### Wrapping / proxies / partial application

As a convenience, allows injecting into runtime arguments.

* `:partial`: Partial application of the function, dependencies are bound when
  the component is provided
* `:into-first`, `:into-last`: Wrap the function and inject dependencies into
  the first/last argument:

```clojure
(defn ring-handler
  {:init/inject [:app/db]
   :init/into :first}
  [request])

(defn http-request
  {:init/inject [:http/client]
   :init/into :last}
  [url opts])
```

TODO: Exact declaration: Better to use `:init/inject-into`, or per argument /
injection point? E.g. `:merge` assumes a map and merges the dependencies into
this argument, `:bind` inject as this argument, removing it at runtime (partial).

#### Instance lifecycle handlers

* `:init/halt-fn` can be a function taking the component instance and perform
  clean-up.

Handlers can:
* Functions, e.g. declared inline or by resolving to a var in the same namespace
* Symbols, resolving to function-valued vars
* Vars containing functions implementing the handler (preferred)

Handlers can also be specified reversely by metadata on a var.  `:init/halts`
must be a keyword, symbol or var referencing an existing component, and registers
the var as a halt handler for the referenced component:

```clojure
(def start-server []
  (server/start))

(def stop-server
  {:init/halts #'start-server}
  [server]
  (server/stop server))
```

### Bindings

Metadata on a _module var_.

### Modules

A var tagged with `:init/module`.  The value must be a map describing the module.

The module is named after the var, but this name can be overwritten with the value
of the `:init/module` tag, similar to components.

* `:binds` is a map of target tags to source tags, stating that the source
  should be provided when the target is requested.

TODO: Easier to treat modules as components, and allowing any component to provide
bindings?  Having two maps for modules (metadata and value) seems inconsistent.
Does it make sense for the module itself to have instances?

### Injection Points

Properties:
* Tag(s) to find matching component(s)
* Cardinality: Zero (optional), One (unique, default), Many ()

Shape:
* Map: Canonical
* Qualified keyword: unique tag
* Vector: Multiple tags
* Set: Injects a set of many deps `#{:app/handler}`

Map:
* `:tags` one or more tags
* `:as` - cardinality (max) / ordinality (min)
  * `:optional`: zero or more; value or `nil`
  * `:unique`: exactly one
  * `:set`: set of zero or more
  * `:seq`: some possibly lazy sequence
  * `:map`: Every matching component keyed by name
* `:defer?` - Lazy, can be dereferenced with `@`/`deref`

Multiple: Map containing:
* `:keys` - injects a map, recurses into keys
  * Vector: Each element is an injection point
  * Map: Keys are target keys, values are injection points (rename)

```clojure
{:keys {:db {:tags [:db/postgres :app/db]
        :handlers #{:app/handler}}}}
```

Short forms:
* Keyword: `{:tags [%] :as :unique}`
* Vector: `{:tags % :as unique}`
* Set: `{:tags (vec %) :as :set}`

Maybe: Vector with unqualified keyword:

```clojure
[:optional :app/db]
[:all :app/db]
[:keys {:db :app/db}]
```

## Discovery

Discovers annotated vars automatically in namespaces.  For that, namespaces
need to be loaded, e.g. using `require`.

### Loading namespaces

Namespaces can be loaded manually, or automatically by examining the classpath.

#### Classpath scanning

Finds namespaces on the Java classpath and loads them.

#### Service loader

Similar to `java.util.ServiceLoader`, searches the classpath for special files
`META-INF/services/init.namespaces`, and loads all namespaces found this way.

Compatible to `ServiceLoader`, so that the files can be merged properly when
generating uberjars by appending them.  This should be done automatically by
uberjar tooling.

## APIs

* `init.component` - components
* `init.config` - work with configurations: Add components, bindings, hooks,
  etc. (do we need `registry`?)
* `init.provider` - create providers, supporting proxies/wrappers
* `init.inject` - injection points
* `init.vars` - create configuration from vars in a namespace
* `init.discovery` - find namespaces
* `init.system` - start/stop systems from a configuration
* `init.core` - convenience entry points, typical users only need this
