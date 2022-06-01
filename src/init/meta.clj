(ns init.meta
  (:require [init.config :as config]
            [init.errors :as errors]
            [init.component :as component]
            [init.inject :as inject]))

;; TODO: Define more functions based on metadata instead of the var?

(def ^:private component-keys
  #{:init/name :init/tags :init/inject :init/stop-fn})

(defn- private? [var]
  (-> var meta :private))

(defn- tagged? [var]
  (->> var meta keys (some component-keys)))

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

(defn- var-tags [var]
  (-> var meta :init/tags set))

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
   provided by other vars, such as `:init/stops`."
  [var]
  (let [[start-fn deps] (producer var)
        tags    (var-tags var)
        stop-fn (when-let [ref (-> var meta :init/stop-fn)]
                  (resolve-hook var ref))]
    (cond-> {:var      var
             :name     (component-name var)
             :start-fn start-fn}
      tags    (assoc :tags tags)
      deps    (assoc :deps deps)
      stop-fn (assoc :stop-fn stop-fn))))

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
;; TODO: Validate: No stop-fn defined yet
;; TODO: Validate: Var is not a component (component-keys)
(defn- register-hook [config var]
  (let [stops (-> var meta :init/stops)
        name  (resolve-component-name var stops)]
    (if-let [component (some-> name config)]
      (config/add-component config (assoc component :stop-fn var) :replace? true)
      (throw (errors/component-not-found-exception config (or name stops) var)))))

;; Functions providing lifecycle handlers for existing components,
;; e.g. stop, maybe suspend, resume, ...
;; TODO: "amend"? "decorators"?
(defn- hook? [var]
  (contains? (meta var) :init/stops))

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
