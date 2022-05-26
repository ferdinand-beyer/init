(ns init.system
  (:require [init.config :as config]))

(defn init
  "Initializes a system from a config map."
  ([config]
   (init config (keys config)))
  ([config keys]

   ))
