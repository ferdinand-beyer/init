(ns init.component-test
  (:require [clojure.test :refer [deftest is testing]]
            [init.component :as component]))

(deftest tag-test
  (let [untagged (component/component {:name ::tag-test
                                       :start-fn (constantly ::started)})
        tagged   (component/tag untagged ::test)]
    (is (not (component/provides? untagged ::test)))
    (is (component/provides? tagged ::test))))

(deftest stop-test
  (testing "calls stop function"
    (let [c (component/component {:name ::stop-fn-test
                                  :start-fn (constantly ::started)
                                  :stop-fn (fn [x] (is (= ::started x)) ::stopped)})]
      (is (= ::stopped (component/stop c ::started)))))

  (testing "calls `.close` on `java.lang.AutoCloseable` instance"
    (let [closed?  (atom false)
          instance (reify java.lang.AutoCloseable
                     (close [_] (reset! closed? true)))
          c        (component/component {:name ::auto-close
                                         :start-fn (constantly instance)})]
      (component/stop c instance)
      (is (true? @closed?)))))
