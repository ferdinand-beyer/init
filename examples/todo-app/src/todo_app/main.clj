(ns todo-app.main
  (:gen-class)
  (:require [init.discovery :refer [load-components]]
            [init.system :as system]))

(def system nil)

(defn- discover []
  (load-components 'todo-app))

(defn- init-system []
  (alter-var-root #'system (fn [s] (or s (-> (discover) system/init)))))

(defn- halt-system []
  (alter-var-root #'system (fn [s] (some-> s system/halt!))))

(defn -main []
  (init-system))

(comment
  (init-system)
  (halt-system)
  )
