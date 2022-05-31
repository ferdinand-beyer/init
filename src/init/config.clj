(ns init.config
  (:require [init.errors :as errors]
            [init.protocols :as protocols]))

(extend-protocol protocols/Selector
  clojure.lang.IPersistentVector
  (tags [v] v)

  clojure.lang.Keyword
  (tags [k] [k])

  clojure.lang.Sequential
  (tags [s] s)

  clojure.lang.IPersistentSet
  (tags [s] s))

(defn- unique? [selector]
  (or (not (satisfies? protocols/Dependency selector))
      (protocols/unique? selector)))

(def ^:private mandatory? unique?)

(defn- provided-tags [component]
  (into #{(protocols/name component)} (protocols/provided-tags component)))

(defn provides?
  "Returns true if `component` provides `selector`."
  [component selector]
  (let [provided (provided-tags component)]
    (every? (fn [t]
              (some #(isa? % t) provided))
            (protocols/tags selector))))

(defn- required-selectors [component]
  (when (satisfies? protocols/Dependent component)
    (protocols/required component)))

(defn add-component
  "Adds a component to the configuration."
  [config component & {:keys [replace?]}]
  {:pre [(satisfies? protocols/Component component)]}
  (let [k (protocols/name component)]
    ;; TODO: Move this somewhere else (into-component?)
    (when-not (qualified-ident? k)
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

(defn- resolve-dep
  [config component selector]
  (let [keys (keys (select config selector))]
    (cond
      (and (mandatory? selector)
           (empty? keys))
      (throw (errors/unsatisfied-dependency-exception config component selector))

      (and (unique? selector)
           (next keys))
      ;; XXX: Could provide a hook to resolve ambuiguity here.
      (throw (errors/ambiguous-dependency-exception config component selector keys))

      :else keys)))

(defn resolve-deps
  "Resolves dependencies of `component` in `config`.  Returns a sequence of
   sequences: For every dependency, a sequence of matching keys."
  [config component]
  (map (partial resolve-dep config component) (required-selectors component)))

(defn resolve-config
  "Returns a map with the keys of `config` and the result of applying
   [[resolve-deps]] to every component value."
  [config]
  (update-vals config #(resolve-deps config %)))
