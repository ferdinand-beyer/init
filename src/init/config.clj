(ns init.config
  (:require [init.component :as component]
            [init.errors :as errors]))

(defn- unique? [selector]
  (not (set? selector)))

(def ^:private mandatory? unique?)

(defn add-component
  "Adds a component to the configuration."
  [config component & {:keys [replace?]}]
  (let [component (component/as-component component)
        name      (:name component)]
    (when (and (not replace?) (contains? config name))
      (throw (errors/duplicate-component-exception name)))
    (assoc config name component)))

;; TODO: Add merge-configs

(defn select
  "Returns a sequence of map entries from `config` with all components
   providing `selector`."
  [config selector]
  (->> config (filter #(component/provides? (val %) selector)) seq))

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
  (when-let [deps (:deps component)]
    (map (partial resolve-dep config component) deps)))

(defn resolve-config
  "Returns a map with the keys of `config` and the result of applying
   [[resolve-deps]] to every component value."
  [config]
  (update-vals config #(resolve-deps config %)))
