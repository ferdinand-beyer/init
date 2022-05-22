(ns init.config
  (:require [clojure.string :as str]
            [init.component :as component]))

(def empty-config {})

(defn find-component
  "Searches `config` for a component by name."
  [config name]
  (get config name))

(defn- invalid-name-exception [n]
  (ex-info (str "Invalid component name: " n ". Must be a qualified keyword.")
           {:error ::invalid-name, :name n}))

(defn- duplicate-name-exception [n]
  (ex-info (str "Duplicate component name: " n)
           {:error ::duplicate-name, :name n}))

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

(defn find-components-by-tag
  "Searches `config` for all components providing `tag`.  `tag` may be a
   keyword or a collection of keywords."
  [config tag]
  (->> config vals (filter #(component/provides? % tag)) seq))

(defn- ambiguous-tag-exception [config tag matching-names]
  (ex-info (str "Ambiguous tag: " tag ". Found multiple candidates: "
                (str/join ", " matching-names))
           {:error ::ambiguous-tag
            :config config
            :tag tag
            :matching-names matching-names}))

(defn find-unique
  "Returns the component in `config` that provides `tag`.  If there are no
   matching components, returns `nil`.  If more than one component provides
   `tag`, throws an ambiguous tag exception."
  [config tag]
  (let [comps (find-components-by-tag config tag)]
    (when (next comps)
      (throw (ambiguous-tag-exception config tag (map component/component-name comps))))
    (first comps)))

(defn- ambiguous? [config tag]
  (next (find-components-by-tag config tag)))

(defn- unique-deps [config]
  (->> (vals config)
       (mapcat component/deps)
       (filter component/unique?)))

(defn- ambiguous-deps [config]
  (->> (unique-deps config)
       (filter #(ambiguous? config (component/tag %)))))

;; Validate:
;; - For each component
;;   For each dep
;;   If unique: require exactly one match (else: unsatisfied / ambiguous dep)

;; Build a dependency graph
;; Get a subset of the config: Components matching required keys, plus their
;; transitive dependencies
;; Get a stable topological order

;; Support filtering components, e.g. for conditionals
