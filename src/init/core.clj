(ns init.core
  (:require [init.graph :as graph]
            [init.system :as system]))

(defn start
  "Starts a config map and returns a system map."
  ([config]
   (start config (keys config)))
  ([config selectors]
   (-> config (graph/dependency-graph) (system/start selectors))))

(defn stop
  "Stops the system."
  ([system]
   (stop system (keys system)))
  ([system selectors]
   (system/stop system selectors)))

(defn stop-on-shutdown
  "Registers a shutdown hook that will stop the `system`."
  [system]
  (.addShutdownHook (Runtime/getRuntime) (Thread. #(stop system))))
