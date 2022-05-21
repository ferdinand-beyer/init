(ns todo-app.server)

(defn start-server
  {:init/tags [:http/server]
   :init/deps [:http.server/port]}
  [port])

(defn stop-server
  #_{:init/halts #'start-server}
  [server])
