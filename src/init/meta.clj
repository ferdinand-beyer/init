(ns init.meta
  (:require [init.config :as config]
            [init.errors :as errors]
            [init.component :as component]
            [init.inject :as inject]))

;; TODO: Define more functions based on metadata instead of the var?

(defn- private? [var]
  (-> var meta :private))

(defn- tagged? [var]
  (->> var meta keys (some #{:init/name :init/provides :init/inject :init/disposer})))

(defn- fn-var? [var]
  (-> var meta :arglists))

(defn- nullary? [var]
  (->> var meta :arglists (not-every? seq)))

(defn component-name
  "Returns the component name of `var`."
  [var]
  (let [m (meta var)
        k (:init/name m)]
    (if (or (nil? k) (true? k))
      (keyword (-> m :ns ns-name name) (-> m :name name))
      k)))

;; TODO: Support scalar :init/provides?
;; Tagging as ::untagged allows us to remove implicit/automatic components
(defn- var-provides [var]
  (into #{} cat [(-> var meta :init/provides)
                 (when-not (tagged? var)
                   [::untagged])]))

(defn- resolve-hook [var ref]
  ;; TODO: Just conform the spec?
  (cond
    (fn? ref) ref
    (var? ref) ref
    (symbol? ref) (ns-resolve (-> var meta :ns) ref)))

(defn- producer [var]
  (if (fn-var? var)
    (inject/producer (-> var meta :init/inject) var)
    [(fn [_] (var-get var)) nil]))

;; TODO: Check arity (w/ warning)?
;; TODO: Think about reloading.  We pass the var here instead of the fn val, is that what we want?
(defn component
  "Returns a component representing `var`.  Will not include information
   provided by other vars, such as `:init/disposes`."
  [var]
  (let [[init deps] (producer var)
        tags (var-provides var)
        halt (when-let [ref (-> var meta :init/disposer)]
               (resolve-hook var ref))]
    (cond-> {:var var
             :name (component-name var)
             :init init}
      deps (assoc :deps deps)
      tags (assoc :tags tags)
      halt (assoc :halt halt))))

(extend-protocol component/AsComponent
  clojure.lang.Var
  (as-component [var] (component var)))

;; TODO: Better story on how to add non-tagged vars
(defn- implicit? [var]
  (and (not (private? var))
       (or (not (fn-var? var))
           (nullary? var))))

(defn- register-component [config var]
  (if (tagged? var)
    (config/add-component config (component var))
    config))

(defn- resolve-component-name [var ref]
  (cond
    (qualified-ident? ref) ref
    (var? ref) (component-name ref)
    (symbol? ref) (some-> (ns-resolve (-> var meta :ns) ref) component-name)))

;; TODO: Validate: Check for unary?
;; TODO: Validate: No disposer defined yet
;; TODO: Validate: No init-tags (not (tagged? var))
(defn- register-hook [config var]
  (let [disposes  (-> var meta :init/disposes)
        name      (resolve-component-name var disposes)]
    (if-let [component (some-> name config)]
      (config/add-component config (assoc component :halt var) :replace? true)
      (throw (errors/component-not-found-exception config (or name disposes) var)))))

;; Functions providing lifecycle handlers for existing components,
;; e.g. halt/stop, maybe suspend, resume, ...
;; TODO: "amend"? "decorators"?
(defn- hook? [var]
  (contains? (meta var) :init/disposes))

;; TODO: Take options to e.g. only consider explicitly tagged vars?
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
