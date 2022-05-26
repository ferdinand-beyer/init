(ns todo-app.server)

(defn start-server
  {:init/tags [:http/server]
   :init/deps [:http.server/port]}
  [port]
  (println "Starting server..."))

(defn stop-server
  {:init/halts #'start-server}
  [server]
  (println "Stopping server..."))
