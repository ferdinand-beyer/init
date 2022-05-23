(ns init.protocols)

(defprotocol Instance
  (-halt [this] "Halts the component instance."))

(defprotocol Dependency
  (-tag [this])
  (-cardinality [this]))

;; Lazy/Indirection: Wrap every instance in a system in "thunk".  Calling it
;; will obtain an instance (the same or a new one, depending on scope).  This
;; could also help with breaking cycles?

(defprotocol Inject
  (-deps [this] "Returns all dependencies required for this injection point")
  ;; Supports injection of maps, collections, delays, etc.
  (-prep [this resolved] "Brings resolved deps into the required shape"))
