(ns init.meta
  (:require [init.config :as config]
            [init.errors :as errors]
            [init.component :as component]
            [init.inject :as inject]))

;; TODO: Define more functions based on metadata instead of the var?

(def ^:private component-keys
  #{:init/name :init/tags :init/inject :init/stop-fn})

(defn- tagged? [var]
  (->> var meta keys (some component-keys)))

(defn- fn-var? [var]
  (-> var meta :arglists))

(defn component-name
  "Returns the component name of `var`."
  [var]
  (let [m (meta var)
        k (:init/name m)]
    (if (or (nil? k) (true? k))
      (keyword (-> m :ns ns-name name) (-> m :name name))
      k)))

(defn- type-hints [var-meta]
  (->> (map (comp eval :tag meta) (:arglists var-meta))
       (cons (:tag var-meta))))

(defn- var-tags [var]
  (let [m (meta var)]
    (->> (concat (type-hints m) (:init/tags m))
         (remove nil?)
         (into #{}))))

(defn- resolve-hook [var ref]
  ;; TODO: Just conform the spec?
  (cond
    (fn? ref) ref
    (var? ref) ref
    (symbol? ref) (ns-resolve (-> var meta :ns) ref)))

(defn- resolve-deps-vars [deps]
  (mapv #(if (var? %) (component-name %) %) deps))

(defn- producer [var]
  (if (fn-var? var)
    (let [[start-fn deps] (inject/producer (-> var meta :init/inject) var)]
      [start-fn (resolve-deps-vars deps)])
    [(fn [_] (var-get var)) nil]))

(defn- stop-fn [var hook-vars]
  (let [stop-ref (-> var meta :init/stop-fn)
        stop-var (:stop hook-vars)]
    ;; TODO: Throw when both are defined?
    (if stop-ref
      (resolve-hook var stop-ref)
      stop-var)))

;; TODO: Think about reloading.  We pass the var here instead of the fn val, is that what we want?
(defn component
  "Returns a component representing `var`."
  ([var]
   (component var nil))
  ([var hook-vars]
   (let [[start-fn deps] (producer var)
         tags            (var-tags var)
         stop-fn         (stop-fn var hook-vars)]
     (-> {:var      var
          :name     (component-name var)
          :start-fn start-fn}
         (cond->
          tags      (assoc :tags tags)
          deps      (assoc :deps deps)
          stop-fn   (assoc :stop-fn stop-fn)
          hook-vars (assoc :hook-vars hook-vars))
         component/component))))

(extend-protocol component/AsComponent
  clojure.lang.Var
  (component [var] (component var)))

(defn- register-component [config var]
  (if (tagged? var)
    (config/add-component config (component var))
    config))

(defn- resolve-component-name [var ref]
  (cond
    (qualified-ident? ref) ref
    (var? ref) (component-name ref)
    (symbol? ref) (some-> (ns-resolve (-> var meta :ns) ref) component-name)))

;; TODO: Validate: No stop-fn defined yet
(defn- with-stop-var [component var]
  (-> component
      (assoc :stop-fn var)
      (assoc-in [:hook-vars :stop] var)))

;; TODO: Validate: Var is not a component (component-keys)
(defn- register-hook [config var]
  (let [stops (-> var meta :init/stops)
        name  (resolve-component-name var stops)]
    (if-let [component (some-> name config)]
      (config/add-component config (with-stop-var component var) :replace? true)
      (throw (errors/component-not-found-exception config (or name stops) var)))))

(defn- hook? [var]
  (contains? (meta var) :init/stops))

;; 🤷 We could extend this to include *all* startable vars, not only tagged ones.
(defn namespace-config
  "Returns a config map with all components defined in `ns`."
  [ns]
  (let [{:keys [config hooks]} (reduce (fn [ctx var]
                                         (if (hook? var)
                                           (update ctx :hooks conj var)
                                           (update ctx :config register-component var)))
                                       {:config {}
                                        :hooks []}
                                       (vals (ns-interns ns)))]
    (reduce register-hook config hooks)))
