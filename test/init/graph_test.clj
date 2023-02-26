(ns init.graph-test
  (:require [clojure.test :refer [deftest is testing]]
            [init.dag :as dag]
            [init.errors :as errors]
            [init.graph :as graph]
            [init.test-support.helpers :refer [ex-info? thrown]]
            [init.test-support.test-config :as tc :refer [make-config]]))

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
          graph  (graph/dependency-graph config)
          dag    (::graph/dag graph)]
      (is (true?  (dag/reachable? dag ::tc/blacksmith ::tc/well)))
      (is (false? (dag/reachable? dag ::tc/lumberjack ::tc/farm))))))

(defn ordered? [expected actual]
  (= expected (keep (set expected) actual)))

(deftest dependency-order-test
  (let [config (tc/settlers)
        graph  (graph/dependency-graph config)]
    (is (= (count config) (count (graph/dependency-order graph))))
    (is (= (count config) (count (graph/dependency-order graph (keys config)))))
    (is (= [::tc/forester ::tc/lumberjack]
           (keys (graph/dependency-order graph [::tc/wood]))))
    (is (= [::tc/farm ::tc/well ::tc/mill ::tc/bakery ::tc/iron-mine]
           (keys (graph/dependency-order graph [::tc/iron]))))
    (let [order (keys (graph/dependency-order graph))]
      (is (ordered? [::tc/farm ::tc/mill ::tc/bakery ::tc/iron-mine ::tc/blacksmith] order))
      (is (ordered? [::tc/well ::tc/bakery] order))
      (is (ordered? [::tc/forester ::tc/lumberjack ::tc/kiln ::tc/blacksmith] order)))))

(deftest reverse-dependency-order-test
  (let [config (tc/settlers)
        graph  (graph/dependency-graph config)]
    (is (= (count config) (count (graph/reverse-dependency-order graph))))
    (is (= (count config) (count (graph/reverse-dependency-order graph (keys config)))))
    (is (= [::tc/blacksmith ::tc/kiln ::tc/lumberjack]
           (keys (graph/reverse-dependency-order graph [::tc/wood]))))
    (is (= [::tc/blacksmith ::tc/iron-mine]
           (keys (graph/reverse-dependency-order graph [::tc/iron]))))
    (let [order (keys (graph/reverse-dependency-order graph))]
      (is (ordered? [::tc/blacksmith ::tc/kiln ::tc/lumberjack ::tc/forester] order))
      (is (ordered? [::tc/blacksmith ::tc/iron-mine ::tc/bakery ::tc/well] order))
      (is (ordered? [::tc/bakery ::tc/mill ::tc/farm] order)))))
