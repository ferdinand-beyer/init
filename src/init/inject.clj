(ns init.inject
  (:require [clojure.spec.alpha :as s]
            [init.errors :as errors]
            [init.specs :as specs]))

;; Producers are pairs [f deps], where deps is a sequence of dependencies
;; and f a function taking a sequence of resolved values and producing a single
;; value.  Resolved values are themselves sequences, as every dependency
;; potentially matches zero or more components.
;; TODO: Is "producer" still an appropriate term for [fn deps]?

;;;; composing producers

;; ? collect functions in a vector, compose them at the end.  This would allow
;; introspection and maybe use in macros?

(defn- compose [f [p deps]]
  [(comp f p) deps])

(defn- combine [f producers]
  (if-not (next producers)
    (compose (comp f vector) (first producers))
    [(fn [inputs]
       (->> producers
            (reduce (fn [[acc inputs] [p deps]]
                      (let [[xs more] (split-at (count deps) inputs)]
                        [(conj acc (p xs)) more]))
                    [[] inputs])
            first
            f))
     (mapcat second producers)]))

;;;; value producers

(defn- unique-producer [selector]
  [ffirst [selector]])

(def ^:private first->set (comp set first))

(defn- set-producer [selector]
  [first->set [selector]])

(defn- map-producer [keys producers]
  (combine #(zipmap keys %) producers))

;;;; injectors

(defn- nullary-injector [f]
  [(fn [_] (f)) nil])

(defn- apply-injector [f producers]
  (combine #(apply f %) producers))

(defn- partial-injector [f producers]
  (combine #(apply partial f %) producers))

(defn- into-args [f merge-fn inputs]
  (fn [& args]
    (apply f (merge-fn args inputs))))

(defn- into-injector [merge-fn f producer]
  (compose #(into-args f merge-fn %) producer))

(defn- into-first-injector [f producer]
  (into-injector
   (fn [[coll & more] inputs]
     (cons (into coll inputs) more))
   f producer))

(defn- into-last-injector [f producer]
  (into-injector
   (fn [args inputs]
     (let [argv (vec args)]
       (conj (pop argv) (into (peek argv) inputs))))
   f producer))

;;;; value parsers

(defn- conform [spec x]
  (let [ret (s/conform spec x)]
    (if (s/invalid? ret)
      (throw (errors/spec-exception spec x))
      ret)))

(def ^:private parse-tag second)

(defmulti ^:private parse-dep first)

(defmethod parse-dep :tag
  [[_ tag]]
  (unique-producer (parse-tag tag)))

(defmethod parse-dep :seq
  [[_ tags]]
  (unique-producer (mapv parse-tag tags)))

(defmethod parse-dep :set
  [[_ tags]]
  (set-producer (into #{} (map parse-tag) tags)))

(defn- parse-keys [tags]
  (let [tags (map parse-tag tags)]
    (map-producer tags (map unique-producer tags))))

(defmulti ^:private parse-val first)

(defmethod parse-val :dep
  [[_ dep]]
  (parse-dep dep))

(defmethod parse-val :keys
  [[_ {:keys [keys]}]]
  (parse-keys keys))

(defmethod parse-val :map
  [[_ m]]
  (map-producer (keys m) (map parse-val (vals m))))

(defmethod parse-val :get
  [[_ {:keys [val path]}]]
  (let [producer (parse-val val)]
    (compose #(get-in % path) producer)))

(defmethod parse-val :apply
  [[_ {:keys [fn args]}]]
  (let [producers (map parse-val args)]
    (apply-injector fn producers)))

(defn value-producer
  "Builds a producer for injected values from the specification `form`."
  [form]
  (parse-val (conform ::specs/inject-val form)))

;;;; injector parsers

(defmulti ^:private parse-inject (fn [conformed _f] (first conformed)))

(defmethod parse-inject :tagged
  [_ f]
  (nullary-injector f))

(defmethod parse-inject :vals
  [[_ vals] f]
  (if (seq vals)
    (apply-injector f (map parse-val vals))
    (nullary-injector f)))

(defmethod parse-inject :partial
  [[_ {:keys [vals]}] f]
  (partial-injector f (map parse-val vals)))

(defmethod parse-inject :into
  [[_ {:keys [clause val]}] f]
  (let [producer (parse-val val)]
    (case clause
      :into-first (into-first-injector f producer)
      :into-last  (into-last-injector f producer))))

(defn producer
  "Builds a producer injecting values into the wrapped function `f`, according
   to the inject specification `form`."
  [form f]
  (parse-inject (conform ::specs/inject form) f))
