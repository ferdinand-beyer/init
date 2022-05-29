(ns todo-app.server
  (:require [org.httpkit.server :as http]))

(defn start-server
  {:init/provides [:init/daemon]
   :init/inject [:ring/handler [:get :app/config :port]]}
  [handler port]
  (http/run-server handler {:port port
                            :legacy-return-value? false}))

(defn stop-server
  {:init/disposes #'start-server}
  [server]
  (http/server-stop! server))
