(ns init.inject
  (:require [clojure.spec.alpha :as s]
            [init.errors :as errors]
            [init.protocols :as protocols]
            [init.specs :as specs]))

(deftype Dependency [selector unique?]
  Object
  (toString [_] (str selector))

  protocols/Selector
  (tags [_] (protocols/tags selector))

  protocols/Dependency
  (unique? [_] unique?))

(defmethod print-method Dependency
  [dep ^java.io.Writer writer]
  (.write writer "#init/")
  (.write writer (if (protocols/unique? dep) "ref" "refset"))
  (print-method (protocols/tags dep) writer))

;;;; transform producers

(defn- decorate-producer [f producer]
  (reify
    protocols/Dependent
    (required [_] (protocols/required producer))

    protocols/Producer
    (produce [_ inputs]
      (f (protocols/produce producer inputs)))))

(defn- combine-producers [f producers]
  (reify
    protocols/Dependent
    (required [_] (mapcat protocols/required producers))

    protocols/Producer
    (produce [_ inputs]
      (->> producers
           (reduce (fn [[acc inputs] p]
                     (let [[cur rem] (split-at (-> p protocols/required count) inputs)]
                       [(conj acc (protocols/produce p cur)) rem]))
                   [[] inputs])
           first
           f))))

;;;; value producers

(defn- unique-producer [selector]
  (let [deps [(->Dependency selector true)]]
    (reify
      protocols/Dependent
      (required [_] deps)

      protocols/Producer
      (produce [_ [x]]
        (assert (= 1 (count x)))
        (first x)))))

(defn- set-producer [selector]
  (let [deps [(->Dependency selector false)]]
    (reify
      protocols/Dependent
      (required [_] deps)

      protocols/Producer
      (produce [_ [x]] (set x)))))

(defn- map-producer [keys producers]
  (combine-producers #(zipmap keys %) producers))

;;;; injectors

(defn- nullary-injector [f]
  (reify
    protocols/Dependent
    (required [_] nil)

    protocols/Producer
    (produce [_ _] (f))))

(defn- apply-injector [f producers]
  (combine-producers #(apply f %) producers))

(defn- partial-injector [f producers]
  (combine-producers #(apply partial f %) producers))

(defn- into-args [f merge-fn inputs]
  (fn [& args]
    (apply f (merge-fn args inputs))))

(defn- into-injector [merge-fn f producer]
  (decorate-producer #(into-args f merge-fn %) producer))

(defn- into-first-injector [f producer]
  (into-injector
   (fn [[a & args] inputs]
     (cons (merge a inputs) args))
   f producer))

(defn- into-last-injector [f producer]
  (into-injector
   (fn [args inputs]
     (let [argv (vec args)]
       (conj (pop argv) (merge (peek argv) inputs))))
   f producer))

;;;; value parsers

(defn- conform [spec x]
  (let [ret (s/conform spec x)]
    (if (s/invalid? ret)
      (throw (errors/spec-exception spec x))
      ret)))

(def ^:private parse-tag second)

(defn- parse-keys [tags]
  (let [tags (map parse-tag tags)]
    (map-producer tags (map unique-producer tags))))

(defmulti ^:private parse-val first)

(defmethod parse-val :tag
  [[_ tag]]
  (unique-producer (parse-tag tag)))

(defmethod parse-val :selector
  [[_ tags]]
  (unique-producer (mapv parse-tag tags)))

(defmethod parse-val :set
  [[_ tags]]
  (set-producer (into #{} (map parse-tag) tags)))

(defmethod parse-val :keys
  [[_ {:keys [keys]}]]
  (parse-keys keys))

(defmethod parse-val :map
  [[_ m]]
  (map-producer (keys m) (map parse-val (vals m))))

(defmethod parse-val :get
  [[_ {:keys [val path]}]]
  (let [producer (parse-val val)]
    (decorate-producer #(get-in % path) producer)))

(defmethod parse-val :apply
  [[_ {:keys [fn args]}]]
  (let [producers (map parse-val args)]
    (apply-injector fn producers)))

(defn value-producer
  "Builds a producer for injected values from the specification `form`."
  [form]
  (parse-val (conform ::specs/inject-val form)))

;;;; injector parsers

(defmulti ^:private parse-into-val first)

(defmethod parse-into-val :keys
  [[_ tags]]
  (parse-keys tags))

(defmethod parse-into-val :map
  [[_ m]]
  (map-producer (keys m) (map parse-val (vals m))))

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
  (let [producer (parse-into-val val)]
    (case clause
      :into-first (into-first-injector f producer)
      :into-last  (into-last-injector f producer))))

(defn producer
  "Builds a producer injecting values into the wrapped function `f`, according
   to the inject specification `form`."
  [form f]
  (parse-inject (conform ::specs/inject form) f))
