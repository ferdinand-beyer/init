(ns init.test-support.test-config
  (:require [init.config :as config]
            [init.protocols :as protocols]))

(defn component
  ([name]
   (component name nil))
  ([name provides]
   (component name provides nil))
  ([name provides requires]
   (reify
     protocols/Component
     (name [_] name)
     (provided-tags [_] provides)

     protocols/Dependent
     (required [_] requires))))

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
   [::blacksmith [:tools] [::iron ::coal]]])

(derive ::fish ::food)
(derive ::bread ::food)
(derive ::charcoal ::coal)

(defn settlers []
  (make-config settlers-components))
