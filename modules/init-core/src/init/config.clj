(ns init.config
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [init.component :as component]
            [weavejester.dependency :as dep]))

(defn find-component
  "Searches `config` for a component by name."
  [config name]
  (get config name))

(defn- invalid-name-exception [n]
  (ex-info (str "Invalid component name: " n ". Must be a qualified keyword.")
           {:reason ::invalid-name, :name n}))

(defn- duplicate-name-exception [n]
  (ex-info (str "Duplicate component name: " n)
           {:reason ::duplicate-name, :name n}))

(defn add-component
  "Adds a component to the configuration."
  [config component & {:keys [replace?]}]
  (let [n (component/component-name component)]
    ;; TODO: Move this somewhere else (into-component?)
    (when-not (component/valid-name? n)
      (throw (invalid-name-exception n)))
    (when (and (not replace?) (get config n))
      (throw (duplicate-name-exception n)))
    (assoc config n component)))

(defn select
  "Searches `config` for all components providing `tag`.  `tag` may be a
   keyword or a collection of keywords."
  [config tag]
  (->> config (filter #(component/provides? (val %) tag)) seq))

(defn- ambiguous-tag-exception [config tag matching-names]
  (ex-info (str "Ambiguous tag: " tag ". Found multiple candidates: "
                (str/join ", " matching-names))
           {:reason ::ambiguous-tag
            :config config
            :tag tag
            :matching-names matching-names}))

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
  (let [tag   (component/tag dep)
        found (select config tag)]
    (cond
      (not (component/unique? dep)) found
      (empty? found) (throw (unsatisfied-dependency-exception config tag))
      (next found) (throw (ambiguous-tag-exception config tag (keys found)))
      :else found)))

(defn resolve-deps
  "Resolves dependencies of `component` in `config`.  Returns a sequence of
   sequences: For every dependency, a sequence of map entries with resolved
   components."
  [config component]
  (map #(resolve-dependency config %) (component/deps component)))

(defn- circular-dependency-exception [config name1 name2]
  (ex-info (str "Circular dependency between components " name1 " and " name2)
           {:reason ::circular-dependency
            :config config
            :names #{name1 name2}}))

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
