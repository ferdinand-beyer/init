(ns init.inject
  (:require [init.protocols :as protocols]))

(defn- unqualified? [k]
  (nil? (namespace k)))

(defn- composite-producer
  [f producers]
  (reify
    protocols/Dependent
    (required [_] (mapcat protocols/required producers))

    protocols/Producer
    (produce [_ deps]
      (->> producers
           (reduce (fn [[acc vals] p]
                     (let [[vs remaining] (split-at (-> p protocols/required count) vals)]
                       [(conj acc (protocols/produce p vs)) remaining]))
                   [[] deps])
           first
           f))))

;;;; Arguments / injection points

(defn- unique-arg [selector]
  (reify
    protocols/Selector
    (tags [_] (protocols/tags selector))

    protocols/Dependency
    (unique? [_] true)

    protocols/Dependent
    (required [arg] [arg])

    protocols/Producer
    (produce [_ [x]]
      (assert (= 1 (count x)))
      (first x))))

(defn- set-arg [selector]
  (reify
    protocols/Selector
    (tags [_] (protocols/tags selector))

    protocols/Dependency
    (unique? [_] false)

    protocols/Dependent
    (required [arg] [arg])

    protocols/Producer
    (produce [_ [x]] (set x))))

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

;; TODO: Use :keys?
(defmethod -parse-arg-clause :map
  [_ body]
  (assert (seq body))
  (assert (every? qualified-keyword? body))
  (map-arg (zipmap body (map unique-arg body))))

;; TODO: Better name?
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

;; TODO: Name anonymous function for better error diagnostics (e.g. on ArityException)

(defn- args-injector [inject-fn f producer]
  (reify
    protocols/Dependent
    (required [_] (protocols/required producer))

    protocols/Producer
    (produce [_ resolved]
      (let [deps (protocols/produce producer resolved)]
        (fn [& args]
          (apply f (inject-fn args deps)))))))

(defn- nullary-injector [f]
  (reify
    protocols/Dependent
    (required [_] nil)

    protocols/Producer
    (produce [_ _] (f))))

(defn- apply-injector [f producers]
  (composite-producer (partial apply f) producers))

(defn- partial-injector [f producers]
  (composite-producer #(apply partial f %) producers))

;; TODO: Assert merge argument is actually a map (better error message)

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
    (-parse-arg (into [:map] body)))) ;; TODO: Extract a function for this, don't depend on the :map spec

(defmethod -parse-inject-clause :into-first
  [_ body f]
  (into-first-injector f (parse-map body)))

(defmethod -parse-inject-clause :into-last
  [_ body f]
  (into-last-injector f (parse-map body)))

;; TODO: Allow scalar value, e.g. `:init/inject ::something`
(defn producer
  "Returns a producer for the injection spec `spec` and a wrapped function `f`."
  [spec f]
  {:pre [(ifn? f)]} ; can be a var
  (if (or (nil? spec) (true? spec) (empty? spec))
    (nullary-injector f)
    (let [[k & body] spec]
      (if (and (some? k) (unqualified? k))
        (-parse-inject-clause k body f)
        (apply-injector f (map -parse-arg spec))))))
