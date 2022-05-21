(ns init.discovery-test
  (:require [clojure.test :refer [deftest is]]
            [init.discovery :as discovery]))

(deftest ns-prefix-pred-test
  (let [pred (discovery/ns-prefix-pred 'init.discovery-test)]
    (is (pred 'init.discovery-test))
    (is (pred 'init.discovery-test.child))
    (is (pred 'init.discovery-test.deeply.nested.child))
    (is (not (pred 'init)))
    (is (not (pred 'init.discovery-test-sibling)))))
