(ns init.config
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [weavejester.dependency :as dep]))

(defprotocol Dependency
  (-dep-tag [this] "Returns the tag the required component needs to provide.")
  (-dep-unique? [this] "Returns true if a unique match is required."))

(defprotocol Component
  (-comp-key [this] "Returns the component key, a qualified keyword.")
  (-comp-provides [this] "Returns additional tags this component provides.")
  (-comp-deps [this] "Returns this component's dependencies."))

(defn valid-key?
  "Returns true if `k` is a valid component key."
  [k]
  (qualified-keyword? k))

(defn- provided-tags [component]
  (into #{(-comp-key component)} (-comp-provides component)))

(defn provides?
  "Returns true if `component` provides `tag`.  `tag` may be a keyword, or a
   collection of keywords, in which case `provides?` checks every tag."
  [component tag]
  (let [provided (provided-tags component)
        derived? (fn [t] (some #(isa? % t) provided))]
    (if (coll? tag)
      (every? derived? tag)
      (derived? tag))))

(defn- invalid-key-exception [k]
  (ex-info (str "Invalid component key: " k ". Must be a qualified keyword.")
           {:reason ::invalid-key, :key k}))

(defn- duplicate-key-exception [k]
  (ex-info (str "Duplicate component key: " k)
           {:reason ::duplicate-key, :key k}))

(defn add-component
  "Adds a component to the configuration."
  [config component & {:keys [replace?]}]
  {:pre [(satisfies? Component component)]}
  (let [k (-comp-key component)]
    ;; TODO: Move this somewhere else (into-component?)
    (when-not (valid-key? k)
      (throw (invalid-key-exception k)))
    (when (and (not replace?) (contains? config k))
      (throw (duplicate-key-exception k)))
    (assoc config k component)))

(defn select
  "Searches `config` for all components providing `tag`.  `tag` may be a
   keyword or a collection of keywords."
  [config tag]
  (->> config (filter #(provides? (val %) tag)) seq))

(defn- ambiguous-tag-exception [config tag matching-keys]
  (ex-info (str "Ambiguous tag: " tag ". Found multiple candidates: "
                (str/join ", " matching-keys))
           {:reason ::ambiguous-tag
            :config config
            :tag tag
            :matching-keys matching-keys}))

(defn find-unique
  "Returns the component in `config` that provides `tag`.  If there are no
   matching components, returns `nil`.  If more than one component provides
   `tag`, throws an ambiguous tag exception."
  [config tag]
  (when-let [found (select config tag)]
    (when (next found)
      (throw (ambiguous-tag-exception config tag (keys found))))
    (-> found first val)))

(defn- unsatisfied-dependency-exception [config tag]
  (ex-info (str "Unsatisfied dependency: No component found providing " tag)
           {:reason ::unsatisfied-dependency
            :config config
            :tag tag}))

(defn- resolve-dependency [config dep]
  (let [tag   (-dep-tag dep)
        found (select config tag)]
    (cond
      (not (-dep-unique? dep)) found
      (empty? found) (throw (unsatisfied-dependency-exception config tag))
      (next found) (throw (ambiguous-tag-exception config tag (keys found)))
      :else found)))

(defn resolve-deps
  "Resolves dependencies of `component` in `config`.  Returns a sequence of
   sequences: For every dependency, a sequence of map entries with resolved
   components."
  [config component]
  (map #(resolve-dependency config %) (-comp-deps component)))

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
  "Builds a dependency graph on the keys in `config`."
  [config]
  (try
    (reduce-kv (fn [g n c]
                 (transduce (comp cat (map key))
                            (completing #(dep/depend %1 n %2))
                            g
                            (resolve-deps config c)))
               (dep/graph)
               config)
    (catch Exception ex
      (throw (translate-exception config ex)))))

(defn- key-comparator [graph]
  (dep/topo-comparator #(compare (str %1) (str %2)) graph))

(defn- keyset
  "Finds all keys that provide given tags."
  [config tags]
  (into #{}
        (comp (mapcat #(select config %))
              (map key))
        tags))

(defn- expand-tags
  "Returns keys in dependency order for all components providing `tags`
   and their transitive dependencies."
  [config tags transitive-fn]
  (let [graph  (dependency-graph config)
        keyset (keyset config tags)]
    (->> (transitive-fn graph keyset)
         (set/union keyset)
         (sort (key-comparator graph)))))

(defn- dependency-order [config tags]
  (expand-tags config tags dep/transitive-dependencies-set))

(defn- reverse-dependency-order [config tags]
  (reverse (expand-tags config tags dep/transitive-dependents-set)))
