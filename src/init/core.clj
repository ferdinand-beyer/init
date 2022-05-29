(ns init.core
  (:require [init.system :as system]))

;; Steps:
;; - Discover components => config
;; - Select subset, validate => graph
;; - Initialise components => system
;; - (Running)
;; - Shutdown => nil

(defn exec
  ([config]
   (exec config [:init/daemon]))
  ([config keys]
   (-> config (system/init keys))))
