(ns init.config
  (:require [init.protocols :as protocols]
            [init.errors :as errors]))

(extend-protocol protocols/Selector
  clojure.lang.IPersistentVector
  (tags [v] v)

  clojure.lang.Keyword
  (tags [k] [k])

  clojure.lang.Sequential
  (tags [s] s)

  clojure.lang.IPersistentSet
  (tags [s] s))

(defn- provided-tags [component]
  (into #{(protocols/name component)} (protocols/provided-tags component)))

(defn provides?
  "Returns true if `component` provides `selector`."
  [component selector]
  (let [provided (provided-tags component)]
    (every? (fn [t]
              (some #(isa? % t) provided))
            (protocols/tags selector))))

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
