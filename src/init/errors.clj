(ns init.errors
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [init.protocols :as protocols]))

(defn spec-exception [spec value]
  (let [data (s/explain-data spec value)]
    (ex-info (str "Value does not conform to spec\n"
                  (with-out-str (s/explain-out data)))
             {:reason  ::failed-spec
              :spec    spec
              :value   value
              :explain data})))

(defn invalid-name-exception [k]
  (ex-info (str "Invalid component name: " k ". Must be a qualified keyword or symbol.")
           {:reason ::invalid-name
            :name   k}))

(defn component-not-found-exception [config name source]
  (ex-info (str "The component " name " referenced by " source
                " does not exist in the config map.")
           {:reason ::component-not-found
            :config config
            :name   name
            :source source}))

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
