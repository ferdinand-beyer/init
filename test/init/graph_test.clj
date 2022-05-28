(ns init.graph-test
  (:require [clojure.test :refer [deftest is testing]]
            [init.graph :as graph]
            [init.errors :as errors]
            [init.test-support.helpers :refer [ex-info? thrown]]
            [init.test-support.test-config :as tc :refer [make-config]]
            [weavejester.dependency :as dep]))

(deftest dependency-graph-test
  (testing "detects unsatisfied dependencies"
    (let [config (dissoc (tc/settlers) ::tc/well)
          ex     (thrown (graph/dependency-graph config))]
      (is (ex-info? ex))
      (is (re-find #"(?i)unsatisfied" (ex-message ex)))
      (is (= ::errors/unsatisfied-dependency (-> ex ex-data :reason)))))

  (testing "detects ambiguous dependencies"
    (let [config (-> tc/settlers-components
                     (conj [::tc/fishery [::tc/fish]])
                     (make-config))
          ex     (thrown (graph/dependency-graph config))]
      (is (ex-info? ex))
      (is (re-find #"(?i)ambiguous" (ex-message ex)))
      (is (= ::errors/ambiguous-dependency (-> ex ex-data :reason)))))

  (testing "detects circular dependencies"
    (let [config (make-config [[::rock [] [::scissors]]
                               [::paper [] [::rock]]
                               [::scissors [] [::paper]]])
          ex     (thrown (graph/dependency-graph config))]
      (is (ex-info? ex))
      (is (re-find #"(?i)circular" (ex-message ex)))
      (is (= ::errors/circular-dependency (-> ex ex-data :reason)))))

  (testing "builds correct graph"
    (let [config (tc/settlers)
          graph  (:graph (graph/dependency-graph config))]
      (is (= (set (keys config))
             (set (dep/nodes graph))))
      (is (true? (dep/depends? graph ::tc/blacksmith ::tc/well)))
      (is (false? (dep/depends? graph ::tc/lumberjack ::tc/farm))))))

(deftest dependency-order-test
  (let [config (tc/settlers)
        graph  (graph/dependency-graph config)]
    (is (= [::tc/forester ::tc/lumberjack]
           (graph/dependency-order graph [::tc/wood])))
    (is (= [::tc/farm ::tc/mill ::tc/well ::tc/bakery ::tc/iron-mine]
           (graph/dependency-order graph [::tc/iron])))
    (is (= [::tc/farm ::tc/mill ::tc/well ::tc/bakery ::tc/forester ::tc/iron-mine ::tc/lumberjack ::tc/kiln ::tc/blacksmith]
           (graph/dependency-order graph (keys config))))))

(deftest reverse-dependency-order-test
  (let [config (tc/settlers)
        graph  (graph/dependency-graph config)]
    (is (= [::tc/blacksmith ::tc/kiln ::tc/lumberjack]
           (graph/reverse-dependency-order graph [::tc/wood])))
    (is (= [::tc/blacksmith ::tc/iron-mine]
           (graph/reverse-dependency-order graph [::tc/iron])))
    (is (= [::tc/blacksmith ::tc/kiln ::tc/lumberjack ::tc/iron-mine ::tc/forester ::tc/bakery ::tc/well ::tc/mill ::tc/farm]
           (graph/reverse-dependency-order graph (keys config))))))
