(ns init.system
  (:require [init.graph :as graph]
            [init.protocols :as protocols]))

;; TODO: Use graph/get-component so that we do not need to hold on the config

;; TODO: Add hooks to report starting/stopping components, e.g. for logging

;; TODO: Halt partially initialised system on exception
(defn- init-component
  [system graph k component]
  {:pre [(satisfies? protocols/Producer component)]}
  (->> (graph/required-keys graph k)
       (map (partial map system))
       (protocols/produce component)
       (assoc system k)))

;; We could keep an atom of the current system and support a special
;; component protocol for system-aware components.  Those could receive
;; the atom and can therefore dereference the system at any time, allowing
;; runtime inspection.
(defn init
  "Initializes a system from a config map."
  ([config]
   (init config (keys config)))
  ([config selectors]
   (init config (graph/dependency-graph config) selectors))
  ([config graph selectors]
   (-> (reduce #(init-component %1 graph %2 (config %2))
               {}
               (graph/dependency-order graph selectors))
       (with-meta {::config config, ::graph graph}))))

;; TODO: Support on-instance disposal, e.g. Closeable
(defn- halt-component!
  [component instance]
  (when (satisfies? protocols/Disposer component)
    (protocols/dispose component instance)))

(defn halt!
  "Halts a system map."
  ([system]
   (halt! system (keys system)))
  ([system selectors]
   (let [{::keys [config graph]} (meta system)]
     (doseq [k (graph/reverse-dependency-order graph selectors)]
       (halt-component! (config k) (system k))))))
