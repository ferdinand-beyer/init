(ns init.system
  (:require [init.config :as config]
            [init.lifecycle :as lifecycle]))

;; TODO: Integrate injections
(defn- unique-dep? [_]
  true)

(defn- prepare-deps
  [config component system]
  (mapv (fn [d r]
          (if (unique-dep? d)
            (-> r first system)
            (into #{} (map system) r)))
        (config/-comp-deps component)
        (config/resolve-deps config component)))

;; Should we keep a map of providers instead of singletons?
(defn- init-component
  [system config k component]
  {:pre [(satisfies? lifecycle/Init component)]}
  (let [deps     (prepare-deps config component system)
        instance (lifecycle/-init component deps)]
    (assoc system k instance)))

;; We could keep an atom of the current system and support a special
;; component protocol for system-aware components.  Those could receive
;; the atom and can therefore dereference the system at any time, allowing
;; runtime inspection.
(defn init
  "Initializes a system from a config map."
  ([config]
   (init config (keys config)))
  ([config selectors]
   (reduce #(init-component %1 config %2 (config %2))
           (with-meta {} {::config config})
           (config/dependency-order config selectors))))

(defn- config [system]
  (-> system meta ::config))

(defn- halt-component!
  [_system _config _k component instance]
  (when (satisfies? lifecycle/Halt component)
    (lifecycle/-halt component instance)))

(defn halt!
  "Halts a system map."
  ([system]
   (halt! system (keys system)))
  ([system selectors]
   (let [config (config system)]
     (doseq [k (config/reverse-dependency-order config selectors)]
       (halt-component! system config k (config k) (system k))))))

(comment
  (require 'init.discovery)

  (-> (init.discovery/load-components 'todo-app)
      (init [:http/server])
      (doto (prn))
      (halt!))

  )
