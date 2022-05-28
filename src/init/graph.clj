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

(defn- resolve-dependency
  [config component selector]
  (let [keys (keys (config/select config selector))]
    (cond
      (and (mandatory? selector)
           (empty? keys))
      (throw (errors/unsatisfied-dependency-exception config component selector))

      (and (unique? selector)
           (next keys))
      ;; XXX: Could provide a hook to resolve ambuiguity here.
      (throw (errors/ambiguous-dependency-exception config component selector keys))

      :else keys)))

(defn- required-selectors [component]
  (when (satisfies? protocols/Dependent component)
    (protocols/required component)))

(defn resolve-deps
  "Resolves dependencies of `component` in `config`.  Returns a sequence of
   sequences: For every dependency, a sequence of matching keys."
  [config component]
  (map (partial resolve-dependency config component) (required-selectors component)))

(defn- resolve-config [config]
  (update-vals config #(resolve-deps config %)))

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
  "Builds a dependency graph on the keys in `config`."
  [config]
  (let [resolved (resolve-config config)
        graph    (build-graph config resolved)]
    {:graph graph
     :config config
     ;; We only keep `resolved` around as the graph does not preserve order.
     :resolved resolved}))

(defn required-keys
  "Returns resolved dependencies of the component with the given `key`, as
   a sequence of sequences, in the declared order."
  [graph key]
  (-> graph :resolved (get key)))

(defn- key-comparator [graph]
  (dep/topo-comparator #(compare (str %1) (str %2)) graph))

(defn- select-keyset
  "Returns the set of config keys that match `selectors`."
  [config selectors]
  (into #{} (mapcat #(keys (config/select config %))) selectors))

(defn- expand-keyset
  [graph transitive-fn keyset]
  (set/union keyset (transitive-fn graph keyset)))

(defn- sort-keys
  [graph keys]
  (sort (key-comparator graph) keys))

(defn- ordered-keys
  ([{:keys [graph config]}]
   (sort-keys graph (keys config)))
  ([{:keys [graph config]} selectors transitive-fn]
   (->> selectors
        (select-keyset config)
        (expand-keyset graph transitive-fn)
        (sort-keys graph))))

(defn dependency-order
  "Returns keys of components satisfying `selectors` in dependency order."
  ([graph]
   (ordered-keys graph))
  ([graph selectors]
   (ordered-keys graph selectors dep/transitive-dependencies-set)))

(defn reverse-dependency-order
  "Returns keys of components satisfying `selectors` in reverse dependency order."
  ([graph]
   (reverse (ordered-keys graph)))
  ([graph selectors]
   (reverse (ordered-keys graph selectors dep/transitive-dependents-set))))
