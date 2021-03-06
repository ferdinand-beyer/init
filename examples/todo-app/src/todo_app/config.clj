(ns todo-app.config
  (:require [aero.core :as aero]
            [clojure.java.io :as io]))

(defn load-config
  {:init/name ::config
   :init/tags [:app/config]}
  []
  (-> (io/resource "config.edn")
      (aero/read-config)))
