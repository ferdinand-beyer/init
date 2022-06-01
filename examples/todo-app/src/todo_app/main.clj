(ns todo-app.main
  (:gen-class)
  (:require [init.discovery :as discovery]
            [init.system :as system]))

(defn -main []
  (-> (discovery/load-components 'todo-app)
      (system/start)))
