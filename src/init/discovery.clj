(ns init.discovery
  (:require [clojure.string :as str]
            [init.meta :as meta]))

(defn- ns-prefix-pred
  "Returns a predicate that matches namespace names (ie. symbols) by prefix."
  [prefix]
  (let [n (name prefix)
        p (str n ".")]
    (fn [ns-name]
      (let [ns (name ns-name)]
        (or (= n ns)
            (str/starts-with? ns p))))))

(defn find-namespaces
  "Searches all loaded namespaces that start with the given prefix."
  [prefix]
  (filter (comp (ns-prefix-pred prefix) ns-name) (all-ns)))

(defn find-components
  "Searches loaded namespaces for components."
  ([]
   (find-components (all-ns)))
  ([namespaces]
   (find-components {} namespaces))
  ([config namespaces]
   ;; TODO: Check for duplicates in merge
   (reduce #(merge %1 (meta/find-components %2)) config namespaces)))

;; Classpath scanning
;; XXX: Move to init.discovery.scan?

(defn classpath-namespaces
  "Returns a sequence of symbols of all namespaces on the classpath.
   Requires `org.clojure/java.classpath` and `org.clojure/tools.namespace`
   on the classpath."
  []
  (let [classpath       (requiring-resolve 'clojure.java.classpath/classpath)
        find-namespaces (requiring-resolve 'clojure.tools.namespace.find/find-namespaces)]
    (find-namespaces (classpath))))

(defn load-namespaces
  "Loads and returns all namespaces with a given prefix on the classpath.

   See [[classpath-namespaces]] for required libraries."
  [prefix]
  (let [ns-names (->> (classpath-namespaces)
                      (filter (ns-prefix-pred prefix)))]
    (run! require ns-names)
    (map find-ns ns-names)))

(defn load-components
  "Loads all components in namespaces prefixed with `ns-prefix`.

   See [[classpath-namespaces]] for required libraries."
  [ns-prefix]
  (find-components (load-namespaces ns-prefix)))
