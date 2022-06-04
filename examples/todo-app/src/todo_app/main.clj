(ns todo-app.main
  (:require [init.core :as init]
            [init.discovery :as discovery])
  (:gen-class))

(defn- tracker
  {:init/stop-fn (fn [_] (println "Stopping app..."))}
  []
  (println "Starting app.."))

(def config (discovery/static-scan '[todo-app]))

(defn -main [& _]
  (-> (init/start config)
      (init/stop-on-shutdown)))
