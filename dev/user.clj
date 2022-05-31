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
  (discovery/load-components 'todo-app))

(defn init []
  (alter-var-root #'system
                  (fn [_]
                    (-> (discover)
                        (system/init))))
  (prn :initialized (keys system))
  :ok)

(defn halt []
  (prn :halting (keys system))
  (alter-var-root #'system (fn [s] (some-> s system/halt!)))
  :ok)

(defn reset []
  (halt)
  (refresh :after `init))

(comment
  (discover)

  (reset)
  (halt)

  (clojure.tools.namespace.repl/clear)
  (clojure.tools.namespace.repl/refresh)
  )
