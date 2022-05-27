(ns todo-app.main
  (:gen-class)
  (:require [clojure.pprint :refer [pprint]]
            [init.discovery :refer [load-components]]
            [init.system :as system]))

(defn server-port
  {:init/provides [:http.server/port]
   :init/inject [:todo-app.config/config]}
  [config]
  (:port config))

;; TODO: Provide a nice API in init.core, including support for shutdown hooks (duct.core-style)
(defn -main []
  (let [config (load-components 'todo-app)
        _      (pprint config)
        system (system/init config)]
    (pprint system)
    (system/halt! system)))

(comment
  (-main)
  )
