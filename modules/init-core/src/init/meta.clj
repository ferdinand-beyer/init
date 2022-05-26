(ns init.meta
  (:require [init.config :as config]
            [init.lifecycle :as lifecycle]))

;; TODO: Define more functions based on metadata instead of the var?

(defn- private? [var]
  (-> var meta :private))

(defn- tagged? [var]
  (->> var meta keys (some #{:init/name :init/tags :init/deps})))

(defn- fn-var? [var]
  (-> var meta :arglists))

(defn- nullary? [var]
  (->> var meta :arglists (not-every? seq)))

(defn component-name
  "Returns the (potential) component name for a var."
  [var]
  (let [m (meta var)
        k (:init/name m)]
    (if (or (nil? k) (true? k))
      (keyword (-> m :ns ns-name name) (-> m :name name))
      k)))

;; Tagging as ::untagged allows us to remove implicit/automatic components
(defn- var-provides [var]
  (into #{} cat [(-> var meta :init/tags)
                 (when-not (tagged? var) [::untagged])]))

;; TODO: Support different inject patterns
(defn- var-deps [var]
  (-> var meta :init/deps))

;; TODO Validate: Deps count match arity
;; TODO: Allow custom injection/initialisation:
;; - partial: Deps are first args, component is a function that takes the
;;   remaining args
;; - merge: Merges deps into first arg
;; (Could be a multimethod dispatching on a metadata key :init/inject)
(defn- var-init [var deps]
  (if (fn-var? var)
    (apply var deps)
    (var-get var)))

(defprotocol IVarComponent
  (-var [this])
  (-with-halt [this halt-var]))

(deftype VarComponent [var halt-var]
  IVarComponent
  (-var [_] var)
  (-with-halt [_ h] (VarComponent. var h))

  config/Component
  (-comp-key [_] (component-name var))
  (-comp-provides [_] (var-provides var))
  (-comp-deps [_] (var-deps var))

  lifecycle/Init
  (-init [_ deps] (var-init var deps))

  lifecycle/Halt
  (-halt [_ instance] (when halt-var (halt-var instance))))

(defmethod print-method VarComponent
  [c writer]
  (.write writer (str "#component[" (-var c) "]")))

(defn- var-component [var]
  (when (or (tagged? var)
            (and (not (private? var))
                 (or (not (fn-var? var))
                     (nullary? var))))
    (VarComponent. var nil)))

(defn- register-component [config var]
  (if-let [component (var-component var)]
    (config/add-component config component)
    config))

(defn- invalid-ref-exception [name]
  (ex-info (str "Referenced component " name " not found")
           {:reason ::invalid-ref
            :name name}))

(defn- find-component [config name]
  (or (get config name)
      (throw (invalid-ref-exception name))))

(defprotocol IResolve
  (-resolve [this config]))

(extend-protocol IResolve
  clojure.lang.Keyword
  (-resolve [kw config] (find-component config kw))

  clojure.lang.Symbol
  (-resolve [sym config] (find-component config (keyword sym)))

  clojure.lang.Var
  (-resolve [var config] (find-component config (component-name var))))

;; TODO: Validate: Check for unary?
(defn- add-halt [config var ref]
  (let [component (-resolve ref config)]
    (config/add-component config (-with-halt component var) :replace? true)))

;; TODO: Validate: No init-tags (not (tagged? var))
(defn- register-hook [config var]
  (let [{:init/keys [halts]} (meta var)]
    (add-halt config var halts)))

;; Functions providing lifecycle handlers for existing components,
;; e.g. halt/stop, maybe suspend, resume, ...
;; TODO: "amend"? "decorators"?
(defn- hook? [var]
  (some #{:init/halts} (-> var meta keys)))

;; halts: disposes? stops? closes?

;; TODO: Take options to e.g. only consider explicitly tagged vars?
(defn find-components
  "Returns a config with all components from `ns`."
  [ns]
  (let [{:keys [config hooks]} (reduce (fn [ctx var]
                                         (if (hook? var)
                                           (update ctx :hooks conj var)
                                           (update ctx :config register-component var)))
                                       {:config {}
                                        :hooks []}
                                       (vals (ns-interns ns)))]
    (reduce register-hook config hooks)))
