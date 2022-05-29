(ns todo-app.handler
  (:require [reitit.ring :as ring]
            [ring.util.response :as resp]))

;; TODO: Provide a means to inject this as a function
(defn home-handler
  {:init/inject [:into-first :app/config]}
  [_request]
  (-> (resp/response "Hello, World!")
      (resp/content-type "text/html")))

(defn router
  {:init/inject [::home-handler]}
  [home-handler]
  (ring/router [["/" home-handler]]))

(defn ring-handler
  {:init/provides [:ring/handler]
   :init/inject [::router]}
  [router]
  (ring/ring-handler router))
