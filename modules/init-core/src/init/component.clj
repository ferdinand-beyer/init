(ns init.component)

;; XXX - Is this a "Reference"?
;; TODO: Move somewhere else _or_ use clearer fn names
(defprotocol Dependency
  (tag [this] "Returns the tag the required component needs to provide.")
  (unique? [this] "Returns true if a unique match is required."))

(defprotocol Component
  (component-name [this] "Returns the component name, a qualified keyword.")
  ;; TODO: "provides"?
  (tags [this] "Returns additional tags this component provides.")
  (deps [this] "Returns this component's dependencies.")

  ;; TODO: Move this into its own protocol?
  ;; TODO: For config, deps will be resolved components.
  ;; TODO: For systems, deps will be (lazy?) instances.
  (init [this deps] "Returns an instance of the component given resolved dependencies."))

(extend-protocol Component
  clojure.lang.IPersistentMap
  (component-name [m] (:name m))
  (tags [m] (:tags m))
  (deps [m] (:deps m))
  (init [m deps] ((:init m) deps)))

(defn valid-name? [n]
  (qualified-keyword? n))

(defn- all-tags [component]
  (into #{(component-name component)} (tags component)))

(defn provides?
  "Returns true if `component` provides `tag`.  `tag` may be a keyword, or a
   collection of keywords, in which case `provides?` checks every tag."
  [component tag]
  (let [provided (all-tags component)
        derived? (fn [t] (some #(isa? % t) provided))]
    (if (coll? tag)
      (every? derived? tag)
      (derived? tag))))
