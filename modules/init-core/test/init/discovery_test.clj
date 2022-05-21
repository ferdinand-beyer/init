(ns init.discovery-test
  (:require [clojure.test :refer [deftest is testing]]
            [init.discovery :as discovery]
            [init.component :as component]))

(defn- with-test-ns-fn [name defs f]
  (assert (nil? (find-ns name)) "Test namespace already exist")
  (let [ns (create-ns name)]
    (try
      (doseq [[n m v] defs]
        (intern ns (with-meta n m) v))
      (f)
      (finally
        (remove-ns name)))))

(defmacro with-test-ns [name defs & body]
  `(with-test-ns-fn ~name ~defs (fn [] ~@body)))

(deftest ns-prefix-pred-test
  (let [pred (discovery/ns-prefix-pred 'init.discovery-test)]
    (is (pred 'init.discovery-test))
    (is (pred 'init.discovery-test.child))
    (is (pred 'init.discovery-test.deeply.nested.child))
    (is (not (pred 'init)))
    (is (not (pred 'init.discovery-test-sibling)))))

(deftest find-components-test
  (testing "finds nothing in empty namespace"
    (with-test-ns 'init.test.empty []
      (is (= {} (discovery/find-components (find-ns 'init.test.empty))))))

  (testing "finds non-function vars"
    (with-test-ns 'test.const '[[simple nil :simple-value]
                                [implicit {:init/name :test/named} :named]
                                [private {:private true} :private]
                                [private-tagged {:private true :init/name true} :private-tagged]
                                [extra {:init/tags [:test/extra]} :extra]]
      (let [components (discovery/find-components 'test.const)
            simple     (:test.const/simple components)]
        (is (= #{:test.const/simple :test/named :test.const/private-tagged :test.const/extra}
               (-> components keys set)))
        (is (= :test.const/simple (component/component-name simple)))
        (is (empty? (component/tags simple)))
        (is (empty? (component/deps simple)))
        (is (= :simple-value (component/init simple nil)))
        (is (= [:test/extra] (-> components :test.const/extra component/tags))))))

  (testing "finds nullary function vars"
    (with-test-ns 'test.nullary [['simple '{:arglists ([])} (fn [] :simple-value)]
                                 ['takes-args '{:arglists ([foo bar])} (fn [_ _])]
                                 ['implicit {:init/name :test/named} (fn [])]
                                 ['private {:private true} (fn [])]
                                 ['private-tagged {:private true :init/name true} (fn [])]
                                 ['extra {:init/tags [:test/extra]} (fn [])]]
      (let [components (discovery/find-components 'test.nullary)
            simple     (:test.nullary/simple components)]
        (is (= #{:test.nullary/simple :test/named :test.nullary/private-tagged :test.nullary/extra}
               (-> components keys set)))
        (is (= :test.nullary/simple (component/component-name simple)))
        (is (empty? (component/tags simple)))
        (is (empty? (component/deps simple)))
        (is (= :simple-value (component/init simple nil)))
        (is (= [:test/extra] (-> components :test.nullary/extra component/tags)))))))
