(ns init.inject-test
  (:require [clojure.test :refer [deftest is testing]]
            [init.inject :as inject]
            [init.protocols :as protocols]))

(defn- assert-unique [form tags]
  (let [producer (inject/value-producer form)
        deps     (protocols/required producer)]
    (is (= 1 (count deps)))
    (is (protocols/unique? (first deps)))
    (is (= tags (protocols/tags (first deps))))
    (is (= ::input (protocols/produce producer [[::input]])))))

(defn- assert-set [form tags]
  (let [producer (inject/value-producer form)
        deps     (protocols/required producer)]
    (is (= 1 (count deps)))
    (is (not (protocols/unique? (first deps))))
    (is (= tags (protocols/tags (first deps))))
    (is (= #{} (protocols/produce producer [nil])))
    (is (= #{1 2 3} (protocols/produce producer [[1 2 3]])))))

(deftest value-producer-test
  (testing "tag"
    (assert-unique ::foo [::foo]))

  (testing "selector"
    (assert-unique [::foo] [::foo])
    (assert-unique [::foo ::bar] [::foo ::bar]))

  (testing "set form"
    (assert-set #{::foo} #{::foo})
    (assert-set #{::foo ::bar} #{::foo ::bar}))

  (testing ":keys clause"
    (let [producer (inject/value-producer [:keys ::foo ::bar])
          deps     (protocols/required producer)]
      (is (= 2 (count deps)))
      (is (every? protocols/unique? deps))
      (is (= [[::foo] [::bar]] (mapv protocols/tags deps)))
      (is (= {::foo 1, ::bar 2} (protocols/produce producer [[1] [2]])))))

  (testing "map form"
    (let [producer (inject/value-producer {:f ::foo, :b ::bar})
          deps     (protocols/required producer)]
      (is (= 2 (count deps)))
      (is (every? protocols/unique? deps))
      (is (= [[::foo] [::bar]] (mapv protocols/tags deps)))
      (is (= {:f 1, :b 2} (protocols/produce producer [[1] [2]])))))

  (testing "nested map form"
    (let [producer (inject/value-producer {:a {:f ::foo, :b ::bar}, :s #{::spam ::eggs}})
          deps     (protocols/required producer)]
      (is (= 3 (count deps)))
      (is (= [[::foo] [::bar] #{::spam ::eggs}] (mapv protocols/tags deps)))
      (is (= [true true false] (mapv protocols/unique? deps)))
      (is (= {:a {:f 1
                  :b 2}
              :s #{3 4}}
             (protocols/produce producer [[1] [2] [3 4]])))))

  (testing ":get form"
    (let [producer (inject/value-producer [:get ::foo :key])
          deps     (protocols/required producer)]
      (is (= 1 (count deps)))
      (is (protocols/unique? (first deps)))
      (is (= [::foo] (protocols/tags (first deps))))
      (is (= ::hello (protocols/produce producer [[{:key ::hello}]])))))

  (testing ":apply form"
    (let [producer (inject/value-producer [:apply - ::foo ::bar])
          deps     (protocols/required producer)]
      (is (= 2 (count deps)))
      (is (every? protocols/unique? deps))
      (is (= [[::foo] [::bar]] (mapv protocols/tags deps)))
      (is (= 5 (protocols/produce producer [[8] [3]]))))))

(defn- assert-nullary [form]
  (let [producer (inject/producer form (fn [] ::value))]
    (is (empty? (protocols/required producer)))
    (is (= ::value (protocols/produce producer nil)))))

(defn- assert-into-first [form]
  (let [producer (inject/producer form assoc)
        deps     (protocols/required producer)]
    (is (= 1 (count deps)))
    (is (= [[::foo]] (mapv protocols/tags deps)))
    (let [f (protocols/produce producer [[1]])]
      (is (= {::foo 1, ::bar 2, ::buzz 3}
             (f {::bar 2} ::buzz 3))))))

(defn- assert-into-last [form]
  (let [producer (inject/producer form (fn [k v m] (assoc m k v)))
        deps     (protocols/required producer)]
    (is (= 1 (count deps)))
    (is (= [[::foo]] (mapv protocols/tags deps)))
    (let [f (protocols/produce producer [[1]])]
      (is (= {::foo 1, ::bar 2, ::buzz 3} (f ::buzz 3 {::bar 2}))))))

(deftest producer-test
  (testing "tagged"
    (assert-nullary true))

  (testing "vals"
    (assert-nullary [])
    (let [producer (inject/producer [::foo ::bar] -)
          deps     (protocols/required producer)]
      (is (= 2 (count deps)))
      (is (= [[::foo] [::bar]] (mapv protocols/tags deps)))
      (is (= 3 (protocols/produce producer [[7] [4]])))))

  (testing ":partial"
    (let [producer (inject/producer [:partial ::foo ::bar] str)
          deps     (protocols/required producer)]
      (is (= 2 (count deps)))
      (is (= [[::foo] [::bar]] (mapv protocols/tags deps)))
      (let [f (protocols/produce producer [["Hello"] [", "]])]
        (is (= "Hello, World" (f "World")))
        (is (= "Hello, Init" (f "Init"))))))

  (testing ":into-first"
    (assert-into-first [:into-first ::foo])
    (assert-into-first [:into-first {::foo ::foo}]))

  (testing ":into-last"
    (assert-into-last [:into-last ::foo])
    (assert-into-last [:into-last {::foo ::foo}])))
