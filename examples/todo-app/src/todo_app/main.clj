(ns todo-app.main
  (:gen-class)
  (:require [clojure.pprint :refer [pprint]]
            [init.discovery :refer [load-components]]))

(defn -main []
  (let [registry (load-components 'todo-app)]
    (pprint registry)))

(comment
  (-main)
  )
