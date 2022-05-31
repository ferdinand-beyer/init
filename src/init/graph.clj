(ns init.graph
  (:require [clojure.set :as set]
            [init.config :as config]
            [init.errors :as errors]
            [weavejester.dependency :as dep]))

(defn- translate-exception [config ex]
  (let [ex-data (ex-data ex)]
    (if (= ::dep/circular-dependency (:reason ex-data))
      (errors/circular-dependency-exception config (:node ex-data) (:dependency ex-data))
      ex)))

(defn- build-graph [config resolved]
  (try
    (reduce-kv (fn [graph name deps]
                 (transduce cat
                            (completing #(dep/depend %1 name %2))
                            graph
                            deps))
               (dep/graph)
               resolved)
    (catch Exception ex
      (throw (translate-exception config ex)))))

(defn dependency-graph
  "Builds a dependency graph on the keys in `config`."
  [config]
  (let [resolved (config/resolve-config config)
        graph    (build-graph config resolved)]
    {:graph graph
     :config config
     ;; We only keep `resolved` around as the graph does not preserve order.
     :resolved resolved}))

(defn get-component
  "Returns a component by name."
  [graph name]
  (-> graph :config name))

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
