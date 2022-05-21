(ns init.discovery
  (:require [clojure.string :as str]))

(defn ns-prefix-pred
  "Returns a predicate that matches namespace names (ie. symbols) by prefix."
  [prefix]
  (let [n (name prefix)
        p (str n ".")]
    (fn [ns-name]
      (let [ns (name ns-name)]
        (or (= n ns)
            (str/starts-with? ns p))))))
