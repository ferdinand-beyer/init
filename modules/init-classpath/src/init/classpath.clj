(ns init.classpath
  (:require [clojure.java.classpath :as classpath]
            [clojure.tools.namespace.find :as find]
            [init.discovery :as discovery]))

(defn find-namespaces
  "Returns a sequence of symbols of all namespaces on the classpath."
  []
  (find/find-namespaces (classpath/classpath)))

(defn load-namespaces
  "Loads and returns all namespaces with a given prefix on the classpath."
  [prefix]
  (let [ns-names (->> (find-namespaces)
                      (filter (discovery/ns-prefix-pred prefix)))]
    (run! require ns-names)
    (map find-ns ns-names)))
