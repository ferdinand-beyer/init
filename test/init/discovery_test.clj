(ns init.discovery-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [init.discovery :as discovery]))

(def this-ns (ns-name *ns*))

(deftest ns-prefix-pred-test
  (let [pred (#'discovery/ns-prefix-pred 'init.discovery-test)]
    (is (pred 'init.discovery-test))
    (is (pred 'init.discovery-test.child))
    (is (pred 'init.discovery-test.deeply.nested.child))
    (is (not (pred 'init)))
    (is (not (pred 'init.discovery-test-sibling)))))

(deftest classpath-namespaces-test
  (let [namespaces (discovery/classpath-namespaces ['init])]
    (is (contains? namespaces this-ns))
    (is (contains? namespaces 'init.core)))
  (let [namespaces (discovery/classpath-namespaces ['init.core 'init.foobar])]
    (is (empty? namespaces)))
  (let [namespaces (discovery/classpath-namespaces ['init] :include? #(str/ends-with? % "core"))]
    (is (= ['init.core] (vec namespaces))))
  (let [namespaces (discovery/classpath-namespaces ['init] :exclude? #(str/ends-with? % "-test"))]
    (is (not (contains? namespaces this-ns)))))
