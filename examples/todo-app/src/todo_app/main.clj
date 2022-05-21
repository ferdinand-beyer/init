(ns todo-app.main
  (:gen-class)
  (:require [init.core :as init]
            [init.classpath :refer [load-components]]))

(defn -main []
  (load-components 'todo-app))

(comment
  (-main)
  )
