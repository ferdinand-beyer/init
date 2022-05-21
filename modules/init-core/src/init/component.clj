(ns init.component)

(defprotocol Component
  (component-name [this] "Returns the component name, a qualified keyword.")
  (tags [this] "Returns additional tags this component provides.")
  (deps [this] "Returns this component's dependencies.")
  (init [this deps] "Returns an instance of the component given resolved dependencies."))

(extend-protocol Component
  clojure.lang.IPersistentMap
  (component-name [m] (:name m))
  (tags [m] (:tags m))
  (deps [m] (:deps m))
  (init [m deps] ((:init m) deps)))

(defn valid-name? [n]
  (qualified-keyword? n))
