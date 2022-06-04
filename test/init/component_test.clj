(ns init.component-test
  (:require [clojure.test :refer [deftest is]]
            [init.component :as component]))

(deftest tag-test
  (let [untagged (component/component {:name ::tag-test
                                       :start-fn (constantly ::started)})
        tagged   (component/tag untagged ::test)]
    (is (not (component/provides? untagged ::test)))
    (is (component/provides? tagged ::test))))
