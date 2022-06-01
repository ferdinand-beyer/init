(ns init.system
  (:require [init.component :as component]
            [init.graph :as graph]))

;; TODO: Use graph/get-component so that we do not need to hold on the config

;; TODO: Add hooks to report starting/stopping components, e.g. for logging

;; TODO: Stop partially started system on exception
(defn- start-component
  [system graph name component]
  (->> (graph/required-keys graph name)
       (map (partial map system))
       (component/start component)
       (assoc system name)))

;; We could keep an atom of the current system and support a special
;; component protocol for system-aware components.  Those could receive
;; the atom and can therefore dereference the system at any time, allowing
;; runtime inspection.
(defn start
  "Starts a system from a config map."
  ([config]
   (start config (keys config)))
  ([config selectors]
   (start config (graph/dependency-graph config) selectors))
  ([config graph selectors]
   (-> (reduce #(start-component %1 graph %2 (config %2))
               {}
               (graph/dependency-order graph selectors))
       (with-meta {::config config, ::graph graph}))))

(defn stop
  "Stops a system map."
  ([system]
   (stop system (keys system)))
  ([system selectors]
   (let [{::keys [config graph]} (meta system)]
     (doseq [k (graph/reverse-dependency-order graph selectors)]
       (component/stop (config k) (system k))))))
