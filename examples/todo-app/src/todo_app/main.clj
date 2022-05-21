(ns todo-app.main
  (:gen-class)
  (:require [init.core :as init]
            [init.classpath :refer [load-namespaces]]))

(defn -main []
  (load-namespaces 'todo-app))

(comment
  (-main)
  )
