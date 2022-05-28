(ns init.errors
  (:require [clojure.string :as str]
            [init.protocols :as protocols]))

(defn invalid-name-exception [k]
  (ex-info (str "Invalid component name: " k ". Must be a qualified keyword.")
           {:reason ::invalid-name
            :name   k}))

(defn duplicate-component-exception [k]
  (ex-info (str "Duplicate component: " k)
           {:reason ::duplicate-component
            :name   k}))

(defn unsatisfied-dependency-exception [config component selector]
  (ex-info (str "Unsatisfied dependency: No component found providing " selector
                " required by " (protocols/name component))
           {:reason    ::unsatisfied-dependency
            :config    config
            :component component
            :selector  selector}))

(defn ambiguous-dependency-exception [config component selector matching-keys]
  (ex-info (str "Ambiguous dependency: " selector " for " (protocols/name component)
                ". Found multiple candidates: " (str/join ", " matching-keys))
           {:reason        ::ambiguous-dependency
            :config        config
            :component     component
            :selector      selector
            :matching-keys matching-keys}))

(defn circular-dependency-exception [config key1 key2]
  (ex-info (str "Circular dependency between components " key1 " and " key2)
           {:reason ::circular-dependency
            :config config
            :keys #{key1 key2}}))
