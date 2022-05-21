(ns init.protocols)

(defprotocol Instance
  (-halt [this] "Halts the component instance."))

(defprotocol Dependency
  (-tag [this])
  (-cardinality [this]))
