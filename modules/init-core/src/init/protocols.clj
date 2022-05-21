(ns init.protocols)

(defprotocol Component
  (-name [this] "Returns the component name, a fully-qualified keyword.")
  (-tags [this] "Returns additional tags this component provides.")
  (-deps [this] "Returns this component's dependencies.")
  (-init [this deps] "Returns an instance of the component given resolved dependencies."))

(defprotocol Instance
  (-halt [this] "Halts the component instance."))

(defprotocol Dependency
  (-tag [this])
  (-cardinality [this]))
