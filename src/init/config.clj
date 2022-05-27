(ns init.config
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [weavejester.dependency :as dep]))

(defprotocol Selector
  "Matches components by tags."
  (-tags [this]))

(extend-protocol Selector
  clojure.lang.IPersistentVector
  (-tags [v] v)

  clojure.lang.Keyword
  (-tags [k] [k])

  clojure.lang.Sequential
  (-tags [s] (vec s))

  clojure.lang.IPersistentSet
  (-tags [s] (vec s)))

(defprotocol Component
  (-name [this] "Returns the component name, a qualified keyword.")
  (-provides [this] "Returns additional tags this component provides.")
  (-requires [this] "Returns selectors for required components."))

(defn- provided-tags [component]
  (into #{(-name component)} (-provides component)))

(defn provides?
  "Returns true if `component` provides `selector`."
  [component selector]
  (let [provided (provided-tags component)]
    (every? (fn [t]
              (some #(isa? % t) provided))
            (-tags selector))))

(defn- invalid-name-exception [k]
  (ex-info (str "Invalid component name: " k ". Must be a qualified keyword.")
           {:reason ::invalid-name
            :name   k}))

(defn- duplicate-component-exception [k]
  (ex-info (str "Duplicate component: " k)
           {:reason ::duplicate-component
            :name   k}))

(defn add-component
  "Adds a component to the configuration."
  [config component & {:keys [replace?]}]
  {:pre [(satisfies? Component component)]}
  (let [k (-name component)]
    ;; TODO: Move this somewhere else (into-component?)
    (when-not (qualified-keyword? k)
      (throw (invalid-name-exception k)))
    (when (and (not replace?) (contains? config k))
      (throw (duplicate-component-exception k)))
    (assoc config k component)))

;; TODO: Add merge-configs

(defn select
  "Returns a sequence of map entries from `config` with all components
   providing `selector`."
  [config selector]
  (->> config (filter #(provides? (val %) selector)) seq))

(defn- unsatisfied-dependency-exception [config component selector]
  (ex-info (str "Unsatisfied dependency: No component found providing " selector
                " required by " (-name component))
           {:reason    ::unsatisfied-dependency
            :config    config
            :component component
            :selector  selector}))

(defn- ambiguous-dependency-exception [config component selector matching-keys]
  (ex-info (str "Ambiguous dependency: " selector " for " (-name component)
                ". Found multiple candidates: " (str/join ", " matching-keys))
           {:reason        ::ambiguous-dependency
            :config        config
            :component     component
            :selector      selector
            :matching-keys matching-keys}))

(defn resolve-dependency
  "Default dependency resolution function.  Resolves to any keys matching `selector`."
  [config _ selector]
  (keys (select config selector)))

(defn resolve-unique
  "Alternative dependency resolution function.  Requires a unique key matching `selector`."
  [config component selector]
  (let [keys (resolve-dependency config component selector)]
    (cond
      (empty? keys) (throw (unsatisfied-dependency-exception config component selector))
      (next keys)   (throw (ambiguous-dependency-exception config component selector keys))
      :else keys)))

(defn resolve-deps
  "Resolves dependencies of `component` in `config`.  Returns a sequence of
   sequences: For every dependency, a sequence of matching keys."
  ([config component]
   (resolve-deps config component resolve-dependency))
  ([config component resolve-fn]
   (map (partial resolve-fn config component) (-requires component))))

(defn- circular-dependency-exception [config key1 key2]
  (ex-info (str "Circular dependency between components " key1 " and " key2)
           {:reason ::circular-dependency
            :config config
            :keys #{key1 key2}}))

(defn- translate-exception [config ex]
  (let [ex-data (ex-data ex)]
    (if (= ::dep/circular-dependency (:reason ex-data))
      (circular-dependency-exception config (:node ex-data) (:dependency ex-data))
      ex)))

(defn dependency-graph
  "Builds a dependency graph on the keys in `config`.  Takes an optional
   `resolve-fn` that can be used to customize dependency resolution, e.g.
   to provide validation, ambiguity resolution, etc."
  ([config]
   (dependency-graph config resolve-dependency))
  ([config resolve-fn]
   (try
     (reduce-kv (fn [g n c]
                  (transduce cat
                             (completing #(dep/depend %1 n %2))
                             g
                             (resolve-deps config c resolve-fn)))
                (dep/graph)
                config)
     (catch Exception ex
       (throw (translate-exception config ex))))))

(defn- key-comparator [graph]
  (dep/topo-comparator #(compare (str %1) (str %2)) graph))

(defn- keyset
  "Finds all keys that provide at least one of the `selectors`."
  [config selectors]
  (into #{}
        (comp (mapcat #(select config %))
              (map key))
        selectors))

(defn- expand-tags
  "Returns keys in dependency order for all components providing `selectors`,
   plus their transitive dependencies."
  [config selectors transitive-fn]
  (let [graph  (dependency-graph config)
        keyset (keyset config selectors)]
    (->> (transitive-fn graph keyset)
         (set/union keyset)
         (sort (key-comparator graph)))))

;; TODO: Define those on the dependency graph?  When we keep the dependency
;; graph around, we can easily query resolved dependencies, and use this as
;; a starting point for validation (unsatisfied / ambiguous). Maybe keep the
;; config as metadata.
;; Config -> DependencyGraph -> System

(defn dependency-order [config selectors]
  (expand-tags config selectors dep/transitive-dependencies-set))

(defn reverse-dependency-order [config selectors]
  (reverse (expand-tags config selectors dep/transitive-dependents-set)))

