(ns init.config
  (:require [init.protocols :as protocols]
            [init.errors :as errors]))

(extend-protocol protocols/Selector
  clojure.lang.IPersistentVector
  (tags [v] v)

  clojure.lang.Keyword
  (tags [k] [k])

  clojure.lang.Sequential
  (tags [s] (vec s))

  clojure.lang.IPersistentSet
  (tags [s] (vec s)))

(defn- provided-tags [component]
  (into #{(protocols/name component)} (protocols/provided-tags component)))

(defn- required-selectors [component]
  (when (satisfies? protocols/Dependent component)
    (protocols/required component)))

(defn provides?
  "Returns true if `component` provides `selector`."
  [component selector]
  (let [provided (provided-tags component)]
    (every? (fn [t]
              (some #(isa? % t) provided))
            (protocols/tags selector))))

(defn add-component
  "Adds a component to the configuration."
  [config component & {:keys [replace?]}]
  {:pre [(satisfies? protocols/Component component)]}
  (let [k (protocols/name component)]
    ;; TODO: Move this somewhere else (into-component?)
    (when-not (qualified-keyword? k)
      (throw (errors/invalid-name-exception k)))
    (when (and (not replace?) (contains? config k))
      (throw (errors/duplicate-component-exception k)))
    (assoc config k component)))

;; TODO: Add merge-configs

(defn select
  "Returns a sequence of map entries from `config` with all components
   providing `selector`."
  [config selector]
  (->> config (filter #(provides? (val %) selector)) seq))

(defn resolve-dependency
  "Default dependency resolution function.  Resolves to any keys matching `selector`."
  [config _ selector]
  (keys (select config selector)))

(defn resolve-deps
  "Resolves dependencies of `component` in `config`.  Returns a sequence of
   sequences: For every dependency, a sequence of matching keys."
  ([config component]
   (resolve-deps config component resolve-dependency))
  ([config component resolve-fn]
   (map (partial resolve-fn config component) (required-selectors component))))
