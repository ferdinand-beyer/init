(ns init.graph
  (:require [clojure.set :as set]
            [init.config :as config]
            [init.errors :as errors]
            [init.protocols :as protocols]
            [weavejester.dependency :as dep]))

;; Defaults to unique.
(defn- unique? [selector]
  (or (not (satisfies? protocols/Dependency selector))
      (protocols/unique? selector)))

(defn- mandatory? [selector]
  (unique? selector))

(defn resolve-dependency
  [config component selector]
  (let [keys (config/resolve-dependency config component selector)]
    (cond
      (and (mandatory? selector)
           (empty? keys))
      (throw (errors/unsatisfied-dependency-exception config component selector))

      (and (unique? selector)
           (next keys))
      (throw (errors/ambiguous-dependency-exception config component selector keys))

      :else keys)))

(defn- resolve-config [config resolve-fn]
  (update-vals config #(config/resolve-deps config % resolve-fn)))

(defn- translate-exception [config ex]
  (let [ex-data (ex-data ex)]
    (if (= ::dep/circular-dependency (:reason ex-data))
      (errors/circular-dependency-exception config (:node ex-data) (:dependency ex-data))
      ex)))

(defn- build-graph [config resolved]
  (try
    (reduce-kv (fn [graph name resolved-deps]
                 (transduce cat
                            (completing #(dep/depend %1 name %2))
                            graph
                            resolved-deps))
               (dep/graph)
               resolved)
    (catch Exception ex
      (throw (translate-exception config ex)))))

(defn dependency-graph
  "Builds a dependency graph on the keys in `config`.  Takes an optional
   `resolve-fn` that can be used to customize dependency resolution, e.g.
   to provide validation, ambiguity resolution, etc."
  ([config]
   (dependency-graph config resolve-dependency))
  ([config resolve-fn]
   (let [resolved (resolve-config config resolve-fn)
         graph    (build-graph config resolved)]
     {:graph graph
      :config config
      :resolved resolved})))

(defn required-keys
  "Returns resolved dependencies of the component with the given `key`, as
   a sequence of sequences."
  [graph key]
  (-> graph :resolved (get key)))

(defn- key-comparator [graph]
  (dep/topo-comparator #(compare (str %1) (str %2)) graph))

;; TODO: Move to config?
(defn- keyset
  "Finds all keys that provide at least one of the `selectors`."
  [config selectors]
  (into #{}
        (comp (mapcat #(config/select config %))
              (map key))
        selectors))

(defn- expand-tags
  "Returns keys in dependency order for all components providing `selectors`,
   plus their transitive dependencies."
  [{:keys [graph config]} selectors transitive-fn]
  (let [keyset (keyset config selectors)]
    (->> (transitive-fn graph keyset)
         (set/union keyset)
         (sort (key-comparator graph)))))

(defn dependency-order
  "Returns keys of components satisfying `selectors` in dependency order."
  [graph selectors]
  (expand-tags graph selectors dep/transitive-dependencies-set))

(defn reverse-dependency-order
  "Returns keys of components satisfying `selectors` in reverse dependency order."
  [graph selectors]
  (reverse (expand-tags graph selectors dep/transitive-dependents-set)))
