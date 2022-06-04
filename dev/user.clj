(ns user
  {:clj-kondo/config
   '{:linters {:unused-namespace {:level :off}
               :unused-referred-var {:level :off}
               :refer-all {:level :off}}}}

  (:require [clojure.repl :refer :all]
            [clojure.tools.namespace.repl :as tools-ns :refer [clear refresh refresh-all]]
            [init.discovery :as discovery]
            [init.system :as system]))

(def system nil)

(defn discover []
  (discovery/scan ['todo-app]))

(defn start []
  (alter-var-root #'system
                  (fn [_]
                    (-> (discover)
                        (system/start))))
  (prn :started (keys system))
  :ok)

(defn stop []
  (prn :stopping (keys system))
  (alter-var-root #'system (fn [s] (some-> s system/stop)))
  :ok)

(defn reset []
  (stop)
  (refresh :after `start))

(comment
  (discover)

  (reset)
  (stop)

  (clojure.tools.namespace.repl/clear)
  (clojure.tools.namespace.repl/refresh)
  )
