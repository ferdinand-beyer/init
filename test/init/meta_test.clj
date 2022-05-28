(ns init.meta-test
  (:require [clojure.test :refer [deftest is testing]]
            [init.meta :as meta]
            [init.protocols :as protocols]))

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

;; TODO: Fix tests -- they assume non-tagged vars to be added automatically.

(deftest find-components-test
  (testing "finds nothing in empty namespace"
    (with-test-ns 'test.empty []
      (is (= {} (meta/find-components 'test.empty)))))

  (testing "finds non-function vars"
    (with-test-ns 'test.const '[[simple nil :simple-value]
                                [implicit {:init/name :test/named} :named]
                                [private {:private true} :private]
                                [private-tagged {:private true :init/name true} :private-tagged]
                                [extra {:init/provides [:test/extra]} :extra]]
      (let [config (meta/find-components 'test.const)
            simple (:test.const/simple config)]
        (is (= #{:test.const/simple :test/named :test.const/private-tagged :test.const/extra}
               (-> config keys set)))
        (is (= :test.const/simple (protocols/name simple)))
        (is (empty? (protocols/required simple)))
        (is (= :simple-value (protocols/produce simple nil)))
        (is (empty? (-> config :test.const/private-tagged protocols/provided-tags)))
        (is (= #{:test/extra} (-> config :test.const/extra protocols/provided-tags))))))

  (testing "finds function vars"
    (with-test-ns 'test.fn [['simple '{:arglists ([])} (fn [] :simple-value)]
                            ['takes-args '{:arglists ([foo bar])} (fn [_ _])]
                            ['implicit {:init/name :test/named} (fn [])]
                            ['private {:private true} (fn [])]
                            ['private-tagged {:private true :init/name true} (fn [])]
                            ['extra {:init/provides [:test/extra]} (fn [])]]
      (let [config (meta/find-components 'test.fn)
            simple (:test.fn/simple config)]
        (is (= #{:test.fn/simple :test/named :test.fn/private-tagged :test.fn/extra}
               (-> config keys set)))
        (is (= :test.fn/simple (protocols/name simple)))
        (is (empty? (protocols/required simple)))
        (is (= :simple-value (protocols/produce simple nil)))
        (is (empty? (-> config :test.fn/private-tagged protocols/provided-tags)))
        (is (= #{:test/extra} (-> config :test.fn/extra protocols/provided-tags))))))

  (testing "finds halt hooks"
    (with-test-ns 'test.hooks [['start '{:init/name true} (fn [] :started)]
                               ['stop '{:arglists ([x])
                                        :init/halts test.hooks/start} (fn [_] :stopped)]]
      (let [config (meta/find-components 'test.hooks)]
        (is (= [:test.hooks/start] (keys config)))
        (is (= :stopped (protocols/dispose (:test.hooks/start config) nil)))))))

;; TODO: Test :init/inject
