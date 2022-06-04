(ns todo-app.main
  (:gen-class)
  (:require [init.discovery :as discovery]
            [init.system :as system]))

(def config (discovery/static-scan '[todo-app]))

(defn -main []
  (system/start config))
