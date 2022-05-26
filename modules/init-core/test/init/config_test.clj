(ns init.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [init.component :as component]
            [init.config :as config]
            [weavejester.dependency :as dep]))

(defn- dep
  ([tag] (dep tag true))
  ([tag unique?]
   (reify component/Dependency
     (tag [_] tag)
     (unique? [_] unique?))))

(defn- ->dep [x]
  (if (satisfies? component/Dependency x) x (dep x)))

(defn- component
  ([name] (component name nil))
  ([name provides] (component name provides nil))
  ([name provides requires]
   (reify component/Component
     (component-name [_] name)
     (tags [_] provides)
     (deps [_] (map ->dep requires)))))

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

(defn- make-config
  ([] (make-config settlers-components))
  ([comps]
   (reduce (fn [config [name provides requires]]
             (config/add-component config (component name provides requires)))
           {}
           comps)))

(defmacro thrown
  "Runs `body` in try-catch and returns the thrown exception."
  [& body]
  `(try
     ~@body
     nil
     (catch Exception e#
       e#)))

(deftest find-component-test
  (is (nil? (config/find-component {} ::foo)))
  (let [comp (component ::foo)]
    (is (= comp (config/find-component {::foo comp} ::foo)))))

(deftest add-component-test
  (let [comp (component ::foo)]
    (is (= comp (-> {}
                    (config/add-component comp)
                    (config/find-component ::foo)))))

  (testing "duplicate component name"
    (let [ex (-> {}
                 (config/add-component (component ::foo))
                 (config/add-component (component ::foo))
                 thrown)]
      (is (re-find #"(?i)duplicate" (ex-message ex)))
      (is (= ::config/duplicate-name (-> ex ex-data :reason)))))

  (testing "replacing components"
    (let [old    (component ::foo)
          new    (component ::foo)
          config (-> {}
                     (config/add-component old)
                     (config/add-component new :replace? true))]
      (is (= new (config/find-component config ::foo))))))

(deftest select-test
  (is (nil? (config/select {} ::foo)))

  (let [config (make-config)]
    (testing "select components by name"
      (is (= (select-keys config [::bakery])
             (into {} (config/select config ::bakery)))))

    (testing "select components by provision"
      (is (= (select-keys config [::well])
             (into {} (config/select config ::water))))
      (is (empty? (config/select config ::silver)))
      (is (= (select-keys config [::bakery ::fishery])
             (into {} (config/select config ::food)))))))

(deftest find-unique-test
  (let [config (make-config (conj settlers-components [::fishery [::fish]]))]
    (is (= (::forester config) (config/find-unique config ::tree)))
    (is (nil? (config/find-unique config ::silver)))
    (let [ex (thrown (config/find-unique config ::food))]
      (is (re-find #"(?i)ambiguous" (ex-message ex)))
      (is (= ::config/ambiguous-tag (-> ex ex-data :reason))))))

(deftest dependency-graph-test
  (testing "detects unsatisfied dependencies"
    (let [config (dissoc (make-config) ::well)
          ex     (thrown (config/dependency-graph config))]
      (is (re-find #"(?i)unsatisfied" (ex-message ex)))
      (is (= ::config/unsatisfied-dependency (-> ex ex-data :reason)))))

  (testing "detects ambiguous dependencies"
    (let [config (make-config (conj settlers-components [::fishery [::fish]]))
          ex     (thrown (config/dependency-graph config))]
      (is (re-find #"(?i)ambiguous" (ex-message ex)))
      (is (= ::config/ambiguous-tag (-> ex ex-data :reason)))))

  (testing "detects circular dependencies"
    (let [config (make-config [[::rock [] [::scissors]]
                               [::paper [] [::rock]]
                               [::scissors [] [::paper]]])
          ex     (thrown (config/dependency-graph config))]
      (is (re-find #"(?i)circular" (ex-message ex)))
      (is (= ::config/circular-dependency (-> ex ex-data :reason)))))

  (testing "builds correct graph"
    (let [config (make-config)
          graph  (config/dependency-graph config)]
      (is (= (set (keys config))
             (set (dep/nodes graph))))
      (is (true? (dep/depends? graph ::blacksmith ::well)))
      (is (false? (dep/depends? graph ::lumberjack ::farm))))))

(deftest dependency-order-test
  (let [config (make-config)]
    (is (= [::forester ::lumberjack]
           (#'config/dependency-order config [::wood])))
    (is (= [::farm ::mill ::well ::bakery ::iron-mine]
           (#'config/dependency-order config [::iron])))
    (is (= [::farm ::mill ::well ::bakery ::forester ::iron-mine ::lumberjack ::kiln ::blacksmith]
           (#'config/dependency-order config (keys config))))))

(deftest reverse-dependency-order-test
  (let [config (make-config)]
    (is (= [::blacksmith ::kiln ::lumberjack]
           (#'config/reverse-dependency-order config [::wood])))
    (is (= [::blacksmith ::iron-mine]
           (#'config/reverse-dependency-order config [::iron])))
    (is (= [::blacksmith ::kiln ::lumberjack ::iron-mine ::forester ::bakery ::well ::mill ::farm]
           (#'config/reverse-dependency-order config (keys config))))))

