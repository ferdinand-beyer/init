(ns init.discovery-test
  (:require [clojure.test :refer [deftest is]]
            [init.discovery :as discovery]))

(deftest ns-prefix-pred-test
  (let [pred (#'discovery/ns-prefix-pred 'init.discovery-test)]
    (is (pred 'init.discovery-test))
    (is (pred 'init.discovery-test.child))
    (is (pred 'init.discovery-test.deeply.nested.child))
    (is (not (pred 'init)))
    (is (not (pred 'init.discovery-test-sibling)))))

(deftest classpath-namespaces-test
  (let [namespaces (discovery/classpath-namespaces)]
    (is (contains? namespaces (ns-name *ns*)))
    (is (contains? namespaces 'init.core)))
  (let [namespaces (discovery/classpath-namespaces ['init])]
    (is (contains? namespaces (ns-name *ns*)))
    (is (contains? namespaces 'init.core)))
  (let [namespaces (discovery/classpath-namespaces ['init.core 'init.foobar])]
    (is (empty? namespaces))))
