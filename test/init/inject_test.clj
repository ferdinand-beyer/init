(ns init.inject-test
  (:require [clojure.test :refer [deftest is testing]]
            [init.inject :as inject]))

(def var-component nil)

(defn- assert-unique [form]
  (let [[init deps] (inject/value-producer form)]
    (is (= 1 (count deps)))
    (is (= form (first deps)))
    (is (= ::input (init [[::input]])))))

(defn- assert-set [form]
  (let [[init deps] (inject/value-producer form)]
    (is (= 1 (count deps)))
    (is (= form (first deps)))
    (is (= #{} (init [nil])))
    (is (= #{1 2 3} (init [[1 2 3]])))))

(deftest value-producer-test
  (testing "tag"
    (assert-unique ::foo))

  (testing "seq of tags"
    (assert-unique [::foo])
    (assert-unique [::foo ::bar]))

  (testing "set of tags"
    (assert-set #{::foo})
    (assert-set #{::foo ::bar}))

  (testing "var"
    (assert-unique #'var-component))

  (testing ":keys form"
    (let [[init deps] (inject/value-producer [:keys ::foo ::bar])]
      (is (= [::foo ::bar] deps))
      (is (= {::foo 1, ::bar 2} (init [[1] [2]])))))

  (testing "map form"
    (let [[init deps] (inject/value-producer {:f ::foo, :b ::bar})]
      (is (= [::foo ::bar] deps))
      (is (= {:f 1, :b 2} (init [[1] [2]])))))

  (testing "nested map form"
    (let [[init deps] (inject/value-producer {:a {:f ::foo, :b ::bar}, :s #{::spam ::eggs}})]
      (is (= [::foo ::bar #{::spam ::eggs}] deps))
      (is (= {:a {:f 1
                  :b 2}
              :s #{3 4}}
             (init [[1] [2] [3 4]])))))

  (testing ":get form"
    (let [[init deps] (inject/value-producer [:get ::foo :key])]
      (is (= [::foo] deps))
      (is (= ::hello (init [[{:key ::hello}]])))))

  (testing ":apply form"
    (let [[init deps] (inject/value-producer [:apply - ::foo ::bar])]
      (is (= [::foo ::bar] deps))
      (is (= 5 (init [[8] [3]]))))))

(defn- assert-nullary [form]
  (let [[init deps] (inject/producer form (fn [] ::value))]
    (is (empty? deps))
    (is (= ::value (init nil)))))

(deftest producer-test
  (testing "tagged"
    (assert-nullary true))

  (testing "vals"
    (assert-nullary [])
    (let [[init deps] (inject/producer [::foo ::bar] -)]
      (is (= [::foo ::bar] deps))
      (is (= 3 (init [[7] [4]])))))

  (testing ":partial"
    (let [[init deps] (inject/producer [:partial ::foo ::bar] str)]
      (is (= [::foo ::bar] deps))
      (let [f (init [["Hello"] [", "]])]
        (is (= "Hello, World" (f "World")))
        (is (= "Hello, Init" (f "Init"))))))

  (testing ":into-first"
    (let [[init deps] (inject/producer [:into-first #{::foo}] conj)]
      (is (= [#{::foo}] deps))
      (let [f (init [[:c]])]
        (is (= [:a :b :c :d] (f [:a :b] :d))))))

  (testing ":into-last"
    (let [[init deps] (inject/producer [:into-last {:b ::foo}] (fn [k v m] (assoc m k v)))]
      (is (= [::foo] deps))
      (let [f (init [[2]])]
        (is (= {:a 1 :b 2 :c 3} (f :c 3 {:a 1})))))))
