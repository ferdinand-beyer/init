(ns todo-app.main
  (:gen-class)
  (:require [init.core :as init]
            [init.discovery :as discovery]))

(def config (discovery/static-scan '[todo-app]))

(defn -main []
  (init/start config))
