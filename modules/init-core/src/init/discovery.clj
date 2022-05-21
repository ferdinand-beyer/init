(ns init.discovery
  (:require [clojure.string :as str]
            [init.registry :as registry]))

;; TODO: Internal -- move to 'impl' namespace?
(defn ns-prefix-pred
  "Returns a predicate that matches namespace names (ie. symbols) by prefix."
  [prefix]
  (let [n (name prefix)
        p (str n ".")]
    (fn [ns-name]
      (let [ns (name ns-name)]
        (or (= n ns)
            (str/starts-with? ns p))))))

;; TODO: Validate: name must be a qualified keyword
(defn- component-name [var]
  (let [m (meta var)
        n (:init/name m)]
    (if (or (nil? n) (true? n))
      (keyword (-> m :ns ns-name name) (-> m :name name))
      n)))

;; TODO: Create a light-weight type that determines the init function from the var?
;; We will potentially have a large number of components that will be pruned.
(defn- component [var init-fn]
  (let [m (meta var)]
    {:var var
     :name (component-name var)
     :tags (:init/tags m)
     :deps (:init/deps m)
     :init init-fn}))

;; TODO: Order: 'halts' must be registered after 'inits'
(defn- find-component [registry name]
  (or (registry name)
      (throw (ex-info (str "Component not found: " name)
                      {:name name}))))

(defn- resolve-component [registry ref]
  (cond
    (keyword? ref) (find-component registry ref)
    (symbol? ref)  (find-component registry (keyword ref))
    (var? ref)     (find-component registry (component-name ref))))

;; TODO: Validate: No init-tags (not (tagged? var))
(defn- register-halt-fn [registry halts var]
  (let [component (resolve-component registry halts)]
    (assoc-in registry [(:name component) :halt] var)))

(defn- tagged? [var]
  (->> var meta keys (some #{:init/name :init/tags :init/deps})))

(defn- private? [var]
  (-> var meta :private))

(defn- nullary? [var]
  (when-let [arglists (-> var meta :arglists)]
    (not-every? seq arglists)))

;; TODO: Allow custom injection/initialisation:
;; - partial: Deps are first args, component is a function that takes the
;;   remaining args
;; - merge: Merges deps into first arg
;; (Could be a multimethod dispatching on a metadata key :init/inject)
(defn- init-fn [_ f]
  ;; Need to return a function [deps] => instance, since we use map components
  ;; that take an unbound function (swallow 'this')
  (partial apply f))

;; TODO Validate: Deps count match arity
(defn- register-fn [registry var f]
  (if (or (tagged? var)
          (and (nullary? var) (not (private? var))))
    (registry/add-component registry (component var (init-fn var f)))
    registry))

;; TODO Validate: No deps!
(defn- register-val [registry var val]
  (if (or (tagged? var) (not (private? var)))
    (registry/add-component registry (component var (constantly val)))
    registry))

(defn- register-var [registry _ var]
  (let [{:init/keys [halts]} (meta var)
        value (var-get var)]
    (cond
      (some? halts) (register-halt-fn registry halts var)
      (fn? value) (register-fn registry var value)
      :else (register-val registry var value))))

(defn ns-components
  "Searches a namespace for components."
  ([ns]
   (ns-components (registry/registry) ns))
  ([registry ns]
   (reduce-kv register-var registry (ns-interns ns))))

(defn find-namespaces
  "Searches all loaded namespaces that start with the given prefix."
  [prefix]
  (filter (comp (ns-prefix-pred prefix) ns-name) (all-ns)))

(defn find-components
  "Searches loaded namespaces for components."
  ([]
   (find-components (all-ns)))
  ([namespaces]
   (find-components (registry/registry) namespaces))
  ([registry namespaces]
   (reduce ns-components registry namespaces)))
