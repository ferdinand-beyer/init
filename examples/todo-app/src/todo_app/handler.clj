(ns todo-app.handler
  (:require [reitit.ring :as ring]
            [ring.middleware.defaults :as defaults]
            [ring.util.response :as resp]))

(defn health-check-handler [_]
  (-> (resp/response "OK")
      (resp/content-type "text/plain")))

(def ^{:init/tags [:reitit/route-data]} probe-routes
  ["/healthz" health-check-handler])

(defn home-handler [_]
  (-> (resp/response "Hello, World!")
      (resp/content-type "text/html")))

(def ^{:init/tags [:reitit/route-data]} main-routes
  ["/" home-handler])

(defn router
  {:init/inject [#{:reitit/route-data}]}
  [data]
  (ring/router data))

(def ^:init/name default-middleware
  [defaults/wrap-defaults defaults/site-defaults])

(defn ring-handler
  {:init/tags [:ring/handler]
   :init/inject [::router ::default-middleware]}
  [router middleware]
  (ring/ring-handler router {:middleware [middleware]}))
