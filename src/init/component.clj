(ns init.component
  (:require [init.errors :as errors]))

(defrecord Component [name init])

(defprotocol AsComponent
  (as-component [x]))

(extend-protocol AsComponent
  Component
  (as-component [c] c)

  clojure.lang.IPersistentMap
  (as-component [{:keys [name] :as m}]
    (when-not (qualified-ident? name)
      (throw (errors/invalid-name-exception name)))
    (map->Component m)))

(defn provided-tags
  "Returns a set of all tags provided by `component`."
  [component]
  (into #{(:name component)} (:tags component)))

(defn provides?
  "Returns true if `component` provides `selector`."
  [component selector]
  (let [provided (provided-tags component)
        matches? (fn [tag] (some #(isa? % tag) provided))]
    (if (coll? selector)
      (every? matches? selector)
      (matches? selector))))

(defn init [component inputs]
  {:pre [(:init component)
         (= (count inputs) (count (:deps component)))]}
  ((:init component) inputs))

;; TODO: Support on-instance disposal, e.g. Closeable
(defn halt! [component value]
  (when-let [halt (:halt component)]
    (halt value)))
