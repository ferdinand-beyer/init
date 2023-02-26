(ns init.graph
  (:require [clojure.set :as set]
            [init.dag :as dag]
            [init.config :as config]
            [init.errors :as errors]))

(defn- make-dag [resolved]
  (transduce (mapcat (fn [[k deps]]
                       (for [keys deps
                             d    keys]
                         [k d])))
             (fn
               ([dag] dag)
               ([dag [k d]] (dag/add-edge dag k d)))
             dag/empty-dag
             resolved))

(defn dependency-graph
  "Builds a dependency graph on the keys in `config`."
  [config]
  (let [resolved   (config/resolve-config config)
        dag        (make-dag resolved)
        comparator (try
                     (dag/topo-comparator dag)
                     (catch clojure.lang.ExceptionInfo e
                       (let [cycle (first (:cycles (ex-data e)))]
                         ;; TODO: Report all cycles?
                         (throw (errors/circular-dependency-exception config (first cycle) (second cycle))))))
        key-order  (sort comparator (keys config))]
    {::dag dag
     ::config config
     ::resolved resolved
     ::key-order key-order}))

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
  [{::keys [dag]} expand-fn keyset]
  (set/union keyset (expand-fn dag keyset)))

(defn- entries [m keys]
  (map #(find m %) keys))

(defn- ordered-entries
  [{::keys [config key-order] :as graph} selectors expand-fn]
  (if (= (keys config) selectors)
    (entries config key-order)
    (->> (keep (->> (select-keyset config selectors)
                    (expand-keyset graph expand-fn))
               key-order)
         (entries config))))

(defn dependency-order
  "Returns config entries providing `selectors` in dependency order."
  ([graph]
   (entries (::config graph) (::key-order graph)))
  ([graph selectors]
   (ordered-entries graph selectors dag/expand-out)))

(defn reverse-dependency-order
  "Returns config entries providing `selectors` in reverse dependency order."
  ([graph]
   (reverse (dependency-order graph)))
  ([graph selectors]
   (reverse (ordered-entries graph selectors dag/expand-in))))
