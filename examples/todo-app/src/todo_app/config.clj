(ns todo-app.config
  (:require [aero.core :as aero]
            [clojure.java.io :as io]))

(defn load-config []
  (-> (io/resource "config.edn")
      (aero/read-config)))
