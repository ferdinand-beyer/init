(ns init.inject
  (:require [init.config :as config]))

(defn- unqualified? [k]
  (nil? (namespace k)))

;; XXX: Just add a (-validate)?
(defprotocol Dependency
  "Extenders must also extend config/Selector."
  (-unique? [this] "Returns true if exactly one value is required."))

;; TODO: Double-check if that is the right name. Producer? Injector?
(defprotocol Producer
  (-deps [this] "Returns the sequence of dependencies")
  (-produce [this resolved] "Builds a value from resolved dependencies."))

(defn requires [producer]
  (-deps producer))

(defn produce [producer resolved]
  {:pre [(= (count resolved) (count (-deps producer)))]}
  (-produce producer resolved))

(defn- composite-producer
  [f producers]
  (reify
    Producer
    (-deps [_] (mapcat -deps producers))
    (-produce [_ resolved]
      (->> producers
           (reduce (fn [[acc vals] p]
                     (let [[vs remaining] (split-at (-> p -deps count) vals)]
                       [(conj acc (-produce p vs)) remaining]))
                   [[] resolved])
           first
           f))))

;;;; Arguments / injection points

(defn- unique-arg [selector]
  (reify
    config/Selector
    (-tags [_] (config/-tags selector))

    Dependency
    (-unique? [_] true)

    Producer
    (-deps [this] [this])
    (-produce [_ [x]]
      (assert (= 1 (count x)))
      (first x))))

(defn- set-arg [selector]
  (reify
    config/Selector
    (-tags [_] (config/-tags selector))

    Dependency
    (-unique? [_] false)

    Producer
    (-deps [this] [this])
    (-produce [_ [x]] (set x))))

(defn- map-arg [m]
  (composite-producer #(zipmap (keys m) %) (vals m)))

;; TODO: Improve error messages for all parsing

(defmulti -parse-arg-clause (fn [clause _body] clause))

(defmethod -parse-arg-clause :unique
  [_ body]
  (assert (seq body))
  (assert (every? qualified-keyword? body))
  (unique-arg body))

(defmethod -parse-arg-clause :set
  [_ body]
  (assert (seq body))
  (assert (every? qualified-keyword? body))
  (set-arg body))

(defmethod -parse-arg-clause :map
  [_ body]
  (assert (seq body))
  (assert (every? qualified-keyword? body))
  (map-arg (zipmap body (map unique-arg body))))

(defprotocol IntoArg
  (-parse-arg [arg] "Parse an argument injection"))

(extend-protocol IntoArg
  clojure.lang.Keyword
  (-parse-arg [tag]
    (assert (qualified-keyword? tag))
    (unique-arg tag))

  clojure.lang.IPersistentVector
  (-parse-arg [[k & body :as v]]
    (assert k)
    (if (unqualified? k)
      (-parse-arg-clause k body)
      (unique-arg v)))

  clojure.lang.IPersistentSet
  (-parse-arg [s]
    (assert (seq s))
    (assert (every? qualified-keyword? s))
    (set-arg s))

  clojure.lang.IPersistentMap
  (-parse-arg [m]
    (assert (seq m))
    (map-arg (update-vals m -parse-arg))))

;;;; Injectors

(defn- args-injector [inject-fn f producer]
  (reify
    Producer
    (-deps [_] (-deps producer))
    (-produce [_ resolved]
      (let [deps (-produce producer resolved)]
        (fn [& args]
          (apply f (inject-fn args deps)))))))

(defn- nullary-injector [f]
  (reify
    Producer
    (-deps [_] nil)
    (-produce [_ _] (f))))

(defn- apply-injector [f producers]
  (composite-producer (partial apply f) producers))

(defn- partial-injector [f producers]
  (composite-producer #(apply partial f %) producers))

;; TODO: Assert maps

(defn- into-first-injector [f producer]
  (args-injector
   (fn [[a & args] deps]
     (cons (merge a deps) args))
   f producer))

(defn- into-last-injector [f producer]
  (args-injector
   (fn [args deps]
     (let [argv (vec args)]
       (conj (pop argv) (merge (peek argv) deps))))
   f producer))

(defmulti -parse-inject-clause (fn [clause _body _f] clause))

(defmethod -parse-inject-clause :partial
  [_ body f]
  (partial-injector f (map -parse-arg body)))

;; TODO: Support [:into-first [:map ::key1 ...]] as well
(defn- parse-map [body]
  {:pre [(seq body)]}
  (if (and (= 1 (count body))
           (map? (first body)))
    (-parse-arg (first body))
    (-parse-arg (into [:map] body))))

(defmethod -parse-inject-clause :into-first
  [_ body f]
  (into-first-injector f (parse-map body)))

(defmethod -parse-inject-clause :into-last
  [_ body f]
  (into-last-injector f (parse-map body)))

;; TODO: Allow single value, e.g. `:init/inject ::something`
(defn injector
  "Returns an injector for the injection `spec` and a wrapped function `f`."
  [spec f]
  (if (or (nil? spec) (true? spec) (empty? spec))
    (nullary-injector f)
    (let [[k & body] spec]
      (if (and (some? k) (unqualified? k))
        (-parse-inject-clause k body f)
        (apply-injector f (map -parse-arg spec))))))
