(ns init.component
  (:require [init.errors :as errors]))

(defrecord Component [name start-fn])

(defprotocol AsComponent
  (component [x] "Coerces `x` to component."))

(extend-protocol AsComponent
  Component
  (component [c] c)

  clojure.lang.IPersistentMap
  (component [{:keys [name] :as m}]
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

(defn tag
  "Returns an updated component that provides `tag`."
  [component tag]
  (update component :tags (fnil conj #{}) tag))

(defn start [component inputs]
  {:pre [(:start-fn component)
         (= (count inputs) (count (:deps component)))]}
  ((:start-fn component) inputs))

;; TODO: Support values with stop semantics, e.g. AutoCloseable
(defn stop [component value]
  (when-let [stop-fn (:stop-fn component)]
    (stop-fn value)))
