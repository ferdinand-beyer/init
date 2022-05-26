(ns init.system
  (:require [init.config :as config]
            [init.lifecycle :as lifecycle]))

(defn- prepare-deps
  [config component system]
  (mapv (fn [d r]
          (if (config/-dep-unique? d)
            (-> r first key system)
            (into #{} (map (comp system key)) r)))
        (config/-comp-deps component)
        (config/resolve-deps config component)))

(defn- init-component
  [system config k component]
  {:pre [(satisfies? lifecycle/Init component)]}
  (let [deps     (prepare-deps config component system)
        instance (lifecycle/-init component deps)]
    (assoc system k instance)))

(defn init
  "Initializes a system from a config map."
  ([config]
   (init config (keys config)))
  ([config tags]
   (reduce #(init-component %1 config %2 (config %2))
           (with-meta {} {::config config})
           (config/dependency-order config tags))))

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
  ([system tags]
   (let [config (config system)]
     (doseq [k (config/reverse-dependency-order config tags)]
       (halt-component! system config k (config k) (system k))))))

(comment
  (require 'init.discovery)

  (-> (init.discovery/load-components 'todo-app)
      (init [:http/server])
      (halt!))

  )
