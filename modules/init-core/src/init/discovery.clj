(ns init.discovery
  (:require [clojure.string :as str]
            [init.meta :as meta]))

;; TODO: Internal -- move to 'impl' namespace?
(defn ns-prefix-pred
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
   (reduce #(merge %1 (meta/find-components %2)) config namespaces)))

(comment
  (-> (find-namespaces 'init)
      (find-components))
  )
