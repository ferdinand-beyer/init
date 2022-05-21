(ns init.registry
  (:require [init.component :as component]))

(defn registry []
  {})

(defn- invalid-name-exception [n]
  (ex-info (str "Invalid component name: " n ". Must be a qualified keyword.")
           {:error ::invalid-name, :name n}))

(defn- duplicate-name-exception [n]
  (ex-info (str "Duplicate component name: " n)
           {:error ::duplicate-name, :name n}))

(defn add-component
  "Adds a component to the registry."
  [registry component & {:keys [allow-replace?]}]
  (let [n (component/component-name component)]
    (when-not (component/valid-name? n)
      (throw (invalid-name-exception n)))
    (when (and (not allow-replace?) (get registry n))
      (throw (duplicate-name-exception n)))
    (assoc registry n component)))

(defn find-component [registry name]
  (get registry name))
