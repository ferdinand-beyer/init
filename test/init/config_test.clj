(ns init.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [init.config :as config]
            [init.errors :as errors]
            [init.test-support.helpers :refer [ex-info? thrown]]
            [init.test-support.test-config :as test-config :refer [component]]))

(deftest add-component-test
  (let [comp (component ::foo)]
    (is (= comp (-> {}
                    (config/add-component comp)
                    (get ::foo)))))

  (testing "duplicate component"
    (let [ex (-> {}
                 (config/add-component (component ::foo))
                 (config/add-component (component ::foo))
                 thrown)]
      (is (ex-info? ex))
      (is (re-find #"(?i)duplicate" (ex-message ex)))
      (is (= ::errors/duplicate-component (-> ex ex-data :reason)))))

  (testing "replacing components"
    (let [old    (component ::foo)
          new    (component ::foo)
          config (-> {}
                     (config/add-component old)
                     (config/add-component new :replace? true))]
      (is (= new (::foo config))))))

(deftest select-test
  (is (nil? (config/select {} ::foo)))

  (let [config (test-config/settlers)]
    (testing "select components by key"
      (is (= (select-keys config [::test-config/bakery])
             (into {} (config/select config ::test-config/bakery)))))

    (testing "select components by provision"
      (is (= (select-keys config [::test-config/well])
             (into {} (config/select config ::test-config/water))))
      (is (empty? (config/select config ::silver)))
      (is (= (select-keys config [::test-config/bakery ::test-config/fishery])
             (into {} (config/select config ::test-config/food)))))))
