(ns init.system-test
  (:require [clojure.test :refer [deftest is testing]]
            [init.config :as config]
            [init.graph :as graph]
            [init.errors :as errors]
            [init.test-support.helpers :refer [thrown ex-info?]]
            [init.system :as system]))

(def config
  (-> {}
      (config/add-component {:name ::bar
                             :deps [::foo]
                             :start-fn (fn [deps] [::bar-from (ffirst deps)])})
      (config/add-component {:name ::foo
                             :start-fn (fn [_] ::foo-value)})))

(deftest start-test
  (testing "normal system start"
    (let [graph (graph/dependency-graph config)]
      (is (= {::foo ::foo-value
              ::bar [::bar-from ::foo-value]}
             (system/start graph (keys config))))))

  (testing "stops partially started system on exception"
    (let [ex      (ex-info "Test exception" {})
          stopped (atom {})
          config  (-> config
                      (assoc-in [::foo :stop-fn] (fn [v] (swap! stopped assoc ::foo v)))
                      (assoc-in [::bar :start-fn] (fn [_] (throw ex))))
          graph  (graph/dependency-graph config)
          ex     (thrown (system/start graph (keys config)))]
      (is (ex-info? ex))
      (is (= ::errors/start-failed (-> ex ex-data :reason)))
      (is (= ::bar (-> ex ex-data :component :name)))
      (is (= {::foo ::foo-value} @stopped)))))

(deftest stop-test
  (testing "stops all components in reverse dependency order"
    (let [stopped (atom [])
          stop-fn (partial swap! stopped conj)
          add     (fn [c [n d]]
                    (config/add-component c {:name n
                                             :deps d
                                             :start-fn (constantly n)
                                             :stop-fn stop-fn}))
          config  (reduce add {} [[::a] [::b [::a]] [::c [::b]] [::d [::c ::a]]])
          system  (-> config graph/dependency-graph (system/start (keys config)))
          result  (system/stop system (keys system))]
      (is (nil? result))
      (is (= [::d ::c ::b ::a] @stopped))))

  (testing "suppresses exceptions"
    (let [stop-fn (fn [n] (fn [_] (throw (ex-info "Test" {:name n}))))
          add     (fn [c [n d]]
                    (config/add-component c {:name n
                                             :deps d
                                             :start-fn (constantly n)
                                             :stop-fn  (stop-fn n)}))
          config  (reduce add {} [[::a] [::b [::a]] [::c [::b]] [::d [::c ::a]]])
          system  (-> config graph/dependency-graph (system/start (keys config)))
          ex      (thrown (system/stop system (keys system)))]
      (is (ex-info? ex))
      (is (= ::errors/stop-failed (-> ex ex-data :reason)))
      (is (= ::d (-> ex ex-data :component :name)))
      (is (= ::d (-> ex ex-cause ex-data :name)))
      (is (= [::c ::b ::a] (mapv #(-> % ex-cause ex-data :name) (.getSuppressed ex)))))))
