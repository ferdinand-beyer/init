(ns init.test-support.test-config
  (:require [init.config :as config]
            [init.component :as component]))

(defn component
  ([name]
   (component name nil))
  ([name provides]
   (component name provides nil))
  ([name provides requires]
   (component/component {:name name
                         :tags provides
                         :deps requires})))

(defn make-config
  [comps]
  (reduce (fn [config [name provides requires]]
            (config/add-component config (component name provides requires)))
          {}
          comps))

(def settlers-components
  [[::bakery [::bread] [::flour ::water]]
   [::well [::water]]
   [::mill [::flour] [::wheat]]
   [::farm [::wheat]]
   [::iron-mine [::iron] [::food]]
   [::kiln [::charcoal] [::wood]]
   [::lumberjack [::wood] [::tree]]
   [::forester [::tree]]
   [::blacksmith [:tools] [::iron ::coal]]
   [::hermit]])

(derive ::fish ::food)
(derive ::bread ::food)
(derive ::charcoal ::coal)

(defn settlers []
  (make-config settlers-components))
