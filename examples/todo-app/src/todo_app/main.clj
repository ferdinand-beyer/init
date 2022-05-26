(ns todo-app.main
  (:gen-class)
  (:require [clojure.pprint :refer [pprint]]
            [init.discovery :refer [load-components]]))

(defn server-port
  {:init/tags [:http.server/port]
   :init/deps [:todo-app.config/config]}
  [config]
  (:port config))

(defn -main []
  (let [config (load-components 'todo-app)]
    (pprint config)))

(comment
  (-main)
  )
