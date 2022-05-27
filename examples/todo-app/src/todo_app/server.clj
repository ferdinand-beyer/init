(ns todo-app.server)

(defn start-server
  {:init/provides [:http/server]
   :init/inject [:http.server/port]}
  [port]
  (println "Starting server..."))

(defn stop-server
  {:init/halts #'start-server}
  [server]
  (println "Stopping server..."))
