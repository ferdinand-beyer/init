(ns init.graph
  (:require [clojure.set :as set]
            [com.stuartsierra.dependency :as dep]
            [init.config :as config]
            [init.errors :as errors]))

(defn- translate-exception [config ex]
  (let [ex-data (ex-data ex)]
    (if (= ::dep/circular-dependency (:reason ex-data))
      (errors/circular-dependency-exception config (:node ex-data) (:dependency ex-data))
      ex)))

(defn- build-graph [config resolved]
  (try
    (transduce (mapcat (fn [[k deps]]
                         (map #(vector k %) (flatten deps))))
               (completing (partial apply dep/depend))
               (dep/graph)
               resolved)
    (catch Exception ex
      (throw (translate-exception config ex)))))

(defn dependency-graph
  "Builds a dependency graph on the keys in `config`."
  [config]
  (let [resolved  (config/resolve-config config)
        graph     (build-graph config resolved)
        key-order (dep/topo-sort graph)]
    (assoc graph
           ::config    config
           ::resolved  resolved
           ::key-order key-order)))

(defn required-keys
  "Returns resolved dependencies of the component with the given `key`, as
   a sequence of sequences, in the declared order."
  [graph key]
  (-> graph ::resolved (get key)))

(defn- select-keyset
  "Returns the set of config keys that match `selectors`."
  [config selectors]
  (into #{} (mapcat #(keys (config/select config %))) selectors))

(defn- expand-keyset
  [graph transitive-fn keyset]
  (set/union keyset (transitive-fn graph keyset)))

(defn- entries [m keys]
  (map #(find m %) keys))

(defn- ordered-entries
  [{::keys [config key-order] :as graph} selectors transitive-fn]
  (if (= (keys config) selectors)
    (entries config key-order)
    (->> (keep (->> (select-keyset config selectors)
                    (expand-keyset graph transitive-fn))
               key-order)
         (entries config))))

(defn dependency-order
  "Returns config entries providing `selectors` in dependency order."
  ([graph]
   (entries (::config graph) (::key-order graph)))
  ([graph selectors]
   (ordered-entries graph selectors dep/transitive-dependencies-set)))

(defn reverse-dependency-order
  "Returns config entries providing `selectors` in reverse dependency order."
  ([graph]
   (reverse (dependency-order graph)))
  ([graph selectors]
   (reverse (ordered-entries graph selectors dep/transitive-dependents-set))))
