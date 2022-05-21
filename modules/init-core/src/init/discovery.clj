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

(defn component-name
  "Returns the (potential) component name for a var."
  [var]
  (let [m (meta var)
        n (:init/name m)]
    (if (or (nil? n) (true? n))
      (keyword (-> m :ns ns-name name) (-> m :name name))
      n)))

(defn- tagged? [var]
  (->> var meta keys (some #{:init/name :init/tags :init/deps})))

(defn- private? [var]
  (-> var meta :private))

(defn- nullary? [var]
  (->> var meta :arglists (not-every? seq)))

;; Tagging as ::untagged allows us to remove implicit/automatic components
(defn- tags [var]
  (into #{} cat [(-> var meta :init/tags)
                 (when-not (tagged? var) [::untagged])]))

;; TODO: Create a light-weight type that determines the init function from the var?
;; We will potentially have a large number of components that will be pruned.
(defn- component [var init-fn]
  {:var var
   :name (component-name var)
   :tags (tags var)
   :deps (-> var meta :init/deps)
   :init init-fn})

(defn- invalid-ref-exception [name]
  (ex-info (str "Referenced component " name " not found")
           {:error ::invalid-ref, :name name}))

(defn- find-component [registry name]
  (or (registry/find-component registry name)
      (throw (invalid-ref-exception name))))

(defn- resolve-component [registry ref]
  (cond
    (keyword? ref) (find-component registry ref)
    (symbol? ref)  (find-component registry (keyword ref))
    (var? ref)     (find-component registry (component-name ref))))

;; TODO Validate: Deps count match arity
;; TODO: Allow custom injection/initialisation:
;; - partial: Deps are first args, component is a function that takes the
;;   remaining args
;; - merge: Merges deps into first arg
;; (Could be a multimethod dispatching on a metadata key :init/inject)
(defn- fn->init [var]
  (when (or (tagged? var)
            (and (nullary? var) (not (private? var))))
    ;; Need to return a function [deps] => instance, since we use map components
    ;; that take an unbound function (swallow 'this')
    ;; TODO: Could var-get to 'pin' to the state and safe one indirection
    (partial apply var)))

;; TODO Validate: No deps!
(defn- val->init [var]
  (when (or (tagged? var) (not (private? var)))
    (fn [_] (var-get var))))

(defn- var->component [var]
  (when-let [init-fn (if (-> var meta :arglists)
                       (fn->init var)
                       (val->init var))]
    (component var init-fn)))

;; Aka a "producer" function
(defn- register-init [registry var]
  (if-let [component (var->component var)]
    (registry/add-component registry component)
    registry))

;; TODO: Registry function to update components?
;; TODO: Validate: Check for unary?
(defn- register-halt-fn [registry var ref]
  (let [component (resolve-component registry ref)]
    ;; TODO: Could var-get to 'pin' to the state and safe one indirection
    (assoc-in registry [(:name component) :halt] var)))

;; TODO: Validate: No init-tags (not (tagged? var))
(defn- register-hook [registry var]
  (let [{:init/keys [halts]} (meta var)]
    (register-halt-fn registry var halts)))

;; Functions providing lifecycle handlers for existing components,
;; e.g. halt/stop, maybe suspend, resume, ...
;; TODO: "amend"? "decorators"?
(defn- hook? [var]
  (some #{:init/halts} (-> var meta keys)))

;; halts: disposes? stops? closes?

;; TODO: Take options to e.g. only consider explicitly tagged vars?
(defn ns-components
  "Searches a namespace for components."
  ([ns]
   (ns-components (registry/registry) ns))
  ([registry ns]
   (let [{:keys [registry hooks]} (reduce (fn [ctx var]
                                            (if (hook? var)
                                              (update ctx :hooks conj var)
                                              (update ctx :registry register-init var)))
                                          {:registry registry
                                           :hooks []}
                                          (vals (ns-interns ns)))]
     (reduce register-hook registry hooks))))

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

(comment
  (-> (find-namespaces 'init)
      (find-components))
  )
