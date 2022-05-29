(ns init.meta
  (:require [init.config :as config]
            [init.protocols :as protocols]
            [init.inject :as inject]))

;; TODO: Define more functions based on metadata instead of the var?

(defn- private? [var]
  (-> var meta :private))

(defn- tagged? [var]
  (->> var meta keys (some #{:init/name :init/provides :init/inject})))

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

;; TODO: Support scalar :init/provides
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

(defn- var-dispose [var halt-var value]
  (if halt-var
    (halt-var value)
    ;; TODO: Can be a function, symbol or var.  Symbols need to be resolved.
    (when-let [ref (-> var meta :init/halt-fn)]
      ((resolve-hook var ref) value))))

(defprotocol IVarComponent
  (-var [this])
  (-with-halt [this halt-var]))

(deftype VarComponent [var producer ?halt-var]
  IVarComponent
  (-var [_] var)
  (-with-halt [_ h] (VarComponent. var producer h))

  protocols/Component
  (name [_] (component-name var))
  (provided-tags [_] (var-provides var))

  protocols/Dependent
  (required [_] (protocols/required producer))

  protocols/Producer
  (produce [_ inputs] (protocols/produce producer inputs))

  protocols/Disposer
  (dispose [_ instance] (var-dispose var ?halt-var instance)))

(defmethod print-method VarComponent
  [c writer]
  (.write writer (str "#component[" (-var c) "]")))

(defn- val-producer [var]
  (reify
    protocols/Dependent
    (required [_] nil)

    protocols/Producer
    (produce [_ _] (var-get var))))

(defn- producer [var]
  (if (fn-var? var)
    (inject/producer (-> var meta :init/inject) var)
    (val-producer var)))

;; TODO: Think about reloading.  We pass the var here instead of the fn val, is that what we want?
(defn component
  "Returns a component representing `var`.  Will not include information
   provided by other vars, such as `:init/halts`."
  [var]
  (VarComponent. var (producer var) nil))

;; TODO: Better story on how to add non-tagged vars
(defn- implicit? [var]
  (and (not (private? var))
       (or (not (fn-var? var))
           (nullary? var))))

(defn- register-component [config var]
  (if (tagged? var)
    (config/add-component config (component var))
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
