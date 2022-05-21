(ns init.discovery.classpath
  (:require [clojure.java.classpath :as classpath]
            [clojure.tools.namespace.find :as find]))

(defn find-namespaces
  "Returns a sequence of symbols of all namespaces on the classpath."
  []
  (find/find-namespaces (classpath/classpath)))
