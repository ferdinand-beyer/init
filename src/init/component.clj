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

(defn start
  "Starts `component` with resolved dependencies as `input`, and returns an
   instance value."
  [component inputs]
  {:pre [(:start-fn component)
         (= (count inputs) (count (:deps component)))]}
  ((:start-fn component) inputs))

(defn stop
  "Stops the instance `value` of the `component`."
  [component value]
  (if-let [stop-fn (:stop-fn component)]
    (stop-fn value)
    (when (instance? java.lang.AutoCloseable value)
      (.close ^java.lang.AutoCloseable value))))
