(ns init.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [init.config :as config]
            [weavejester.dependency :as dep]))

(defn- component
  ([key] (component key nil))
  ([key provides] (component key provides nil))
  ([key provides requires]
   (reify config/Component
     (-comp-key [_] key)
     (-comp-provides [_] provides)
     (-comp-deps [_] requires))))

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
   (reduce (fn [config [key provides requires]]
             (config/add-component config (component key provides requires)))
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

(deftest add-component-test
  (let [comp (component ::foo)]
    (is (= comp (-> {}
                    (config/add-component comp)
                    (get ::foo)))))

  (testing "duplicate component key"
    (let [ex (-> {}
                 (config/add-component (component ::foo))
                 (config/add-component (component ::foo))
                 thrown)]
      (is (re-find #"(?i)duplicate" (ex-message ex)))
      (is (= ::config/duplicate-key (-> ex ex-data :reason)))))

  (testing "replacing components"
    (let [old    (component ::foo)
          new    (component ::foo)
          config (-> {}
                     (config/add-component old)
                     (config/add-component new :replace? true))]
      (is (= new (::foo config))))))

(deftest select-test
  (is (nil? (config/select {} ::foo)))

  (let [config (make-config)]
    (testing "select components by key"
      (is (= (select-keys config [::bakery])
             (into {} (config/select config ::bakery)))))

    (testing "select components by provision"
      (is (= (select-keys config [::well])
             (into {} (config/select config ::water))))
      (is (empty? (config/select config ::silver)))
      (is (= (select-keys config [::bakery ::fishery])
             (into {} (config/select config ::food)))))))

(deftest dependency-graph-test
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

(deftest resolve-unique-test
  (testing "detects unsatisfied dependencies"
    (let [config (dissoc (make-config) ::well)
          ex     (thrown (config/dependency-graph config config/resolve-unique))]
      (is (re-find #"(?i)unsatisfied" (ex-message ex)))
      (is (= ::config/unsatisfied-dependency (-> ex ex-data :reason)))))

  (testing "detects ambiguous dependencies"
    (let [config (make-config (conj settlers-components [::fishery [::fish]]))
          ex     (thrown (config/dependency-graph config config/resolve-unique))]
      (is (re-find #"(?i)ambiguous" (ex-message ex)))
      (is (= ::config/ambiguous-tag (-> ex ex-data :reason))))))

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

