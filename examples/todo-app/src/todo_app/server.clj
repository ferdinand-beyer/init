(ns todo-app.server
  (:require [org.httpkit.server :as http]))

(defn start-server
  {:init/tags [:init/daemon]
   :init/inject [:ring/handler [:get :app/config :port]]}
  [handler port]
  (http/run-server handler {:port port
                            :legacy-return-value? false}))

(defn stop-server
  {:init/stops #'start-server}
  [server]
  (http/server-stop! server))
