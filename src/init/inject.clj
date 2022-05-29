(ns init.inject
  (:require [clojure.spec.alpha :as s]
            [init.protocols :as protocols]
            [init.specs :as specs]))

(deftype Dependency [selector unique?]
  protocols/Selector
  (tags [_] (protocols/tags selector))

  protocols/Dependency
  (unique? [_] unique?))

(defmethod print-method Dependency
  [dep ^java.io.Writer writer]
  (.write writer "#init/")
  (.write writer (if (protocols/unique? dep) "ref" "refset"))
  (print-method (protocols/tags dep) writer))

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

(defn- composite-producer
  [f producers]
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

(defn- map-producer [keys producers]
  (composite-producer #(zipmap keys %) producers))

;;;; injectors: function-wrapping producers

(defn- nullary-injector [f]
  (reify
    protocols/Dependent
    (required [_] nil)

    protocols/Producer
    (produce [_ _] (f))))

(defn- default-injector [f producers]
  (composite-producer #(apply f %) producers))

(defn- partial-injector [f producers]
  (composite-producer #(apply partial f %) producers))

(defn- inject-args [f inject-fn vals]
  (fn [& args]
    (apply f (inject-fn args vals))))

(defn- args-injector [inject-fn f producer]
  (reify
    protocols/Dependent
    (required [_] (protocols/required producer))

    protocols/Producer
    (produce [_ inputs]
      (inject-args f inject-fn (protocols/produce producer inputs)))))

(defn- into-first-injector [f producer]
  (args-injector
   (fn [[a & args] inputs]
     (cons (merge a inputs) args))
   f producer))

(defn- into-last-injector [f producer]
  (args-injector
   (fn [args inputs]
     (let [argv (vec args)]
       (conj (pop argv) (merge (peek argv) inputs))))
   f producer))

;;;; value parsers

(defn- conform [spec x]
  (let [ret (s/conform spec x)]
    (if (s/invalid? ret)
      (throw (ex-info "Values does not conform to spec" (s/explain-data spec x)))
      ret)))

(def ^:private parse-tag second)

(defn- parse-keys [tags]
  (let [tags (map parse-tag tags)]
    (map-producer tags (map unique-producer tags))))

(defmulti ^:private parse-val-clause :clause)

(defmethod parse-val-clause :unique
  [{:keys [tags]}]
  (unique-producer (mapv parse-tag tags)))

(defmethod parse-val-clause :set
  [{:keys [tags]}]
  (set-producer (mapv parse-tag tags)))

(defmethod parse-val-clause :map
  [{:keys [tags]}]
  (parse-keys tags))

(defmulti ^:private parse-val first)

(defmethod parse-val :tag
  [[_ tag]]
  (unique-producer (parse-tag tag)))

(defmethod parse-val :selector
  [[_ tags]]
  (unique-producer (mapv parse-tag tags)))

(defmethod parse-val :clause
  [[_ clause]]
  (parse-val-clause clause))

(defmethod parse-val :set
  [[_ tags]]
  (set-producer (into #{} (map parse-tag) tags)))

(defmethod parse-val :map
  [[_ m]]
  (map-producer (keys m) (map parse-val (vals m))))

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
    (default-injector f (map parse-val vals))
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
