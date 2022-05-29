(ns init.meta-test
  (:require [clojure.test :refer [are deftest is testing]]
            [init.meta :as meta]
            [init.protocols :as protocols]))

(defn ^:init/name simple-component [] ::simple)

(defn implicitly-named-component [] ::implicit-name)

(defn explicitly-named-component
  {:init/name ::named}
  []
  ::explicit-name)

(defn providing-component
  {:init/provides [::extra]}
  []
  ::provides)

(def ^:init/name const-component ::const)

(defn producer-component
  {:init/inject [::const ::named]}
  [const named]
  [:called-with const named])

(defn partially-injected-component
  {:init/inject [:partial ::const]}
  [injected runtime-arg]
  [:called-with injected runtime-arg])

(defn disposer [x] [:disposed x])

(defn disposer-fn-component
  {:init/halt-fn disposer}
  []
  ::disposer-fn)

(defn disposer-var-component
  {:init/halt-fn #'disposer}
  []
  ::disposer-var)

(defn disposer-symbol-component
  {:init/halt-fn 'disposer}
  []
  ::disposer-symbol)

(deftest component-test
  (testing "extends protocols"
    (let [c (meta/component #'simple-component)]
      (is (satisfies? protocols/Component c))
      (is (satisfies? protocols/Dependent c))
      (is (satisfies? protocols/Producer c))
      (is (satisfies? protocols/Disposer c))))

  (testing "get wrapped var"
    (is (= #'simple-component (meta/-var (meta/component #'simple-component)))))

  (testing "component names"
    (is (= ::simple-component (protocols/name (meta/component #'simple-component))))
    (is (= ::implicitly-named-component (protocols/name (meta/component #'implicitly-named-component))))
    (is (= ::named (protocols/name (meta/component #'explicitly-named-component)))))

  (testing "provided tags"
    (is (empty? (protocols/provided-tags (meta/component #'simple-component))))
    (is (= #{::extra} (protocols/provided-tags (meta/component #'providing-component)))))

  (testing "constant component"
    (let [c (meta/component #'const-component)]
      (is (empty? (protocols/required c)))
      (is (= const-component (protocols/produce c nil)))
      (is (nil? (protocols/dispose c const-component)))))

  (testing "producer dependency injection"
    (let [c    (meta/component #'producer-component)
          deps (protocols/required c)]
      (is (every? #(satisfies? protocols/Selector %) deps))
      (is (every? #(satisfies? protocols/Dependency %) deps))
      (is (= [[::const] [::named]] (map protocols/tags deps)))
      (is (every? protocols/unique? deps))
      (is (= [:called-with :const :named] (protocols/produce c [[:const] [:named]])))))

  (testing "partial dependency injection"
    (let [c (meta/component #'partially-injected-component)]
      (is (= [[::const]] (map protocols/tags (protocols/required c))))
      (let [f (protocols/produce c [[:injected]])]
        (is (= [:called-with :injected :runtime] (f :runtime))))))

  (testing "disposing components"
    (are [var] (= [:disposed (var)] (protocols/dispose (meta/component var) (var)))
      #'disposer-fn-component
      #'disposer-var-component
      #'disposer-symbol-component)))

;; TODO: Fix tests -- they assume non-tagged vars to be added automatically.
#_
(deftest find-components-test
  (testing "finds nothing in empty namespace"
    (with-test-ns 'test.empty []
      (is (= {} (meta/find-components 'test.empty)))))

  (testing "finds non-function vars"
    (with-test-ns 'test.const '[[simple nil :simple-value]
                                [implicit {:init/name :test/named} :named]
                                [private {:private true} :private]
                                [private-tagged {:private true :init/name true} :private-tagged]
                                [extra {:init/provides [:test/extra]} :extra]]
      (let [config (meta/find-components 'test.const)
            simple (:test.const/simple config)]
        (is (= #{:test.const/simple :test/named :test.const/private-tagged :test.const/extra}
               (-> config keys set)))
        (is (= :test.const/simple (protocols/name simple)))
        (is (empty? (protocols/required simple)))
        (is (= :simple-value (protocols/produce simple nil)))
        (is (empty? (-> config :test.const/private-tagged protocols/provided-tags)))
        (is (= #{:test/extra} (-> config :test.const/extra protocols/provided-tags))))))

  (testing "finds function vars"
    (with-test-ns 'test.fn [['simple '{:arglists ([])} (fn [] :simple-value)]
                            ['takes-args '{:arglists ([foo bar])} (fn [_ _])]
                            ['implicit {:init/name :test/named} (fn [])]
                            ['private {:private true} (fn [])]
                            ['private-tagged {:private true :init/name true} (fn [])]
                            ['extra {:init/provides [:test/extra]} (fn [])]]
      (let [config (meta/find-components 'test.fn)
            simple (:test.fn/simple config)]
        (is (= #{:test.fn/simple :test/named :test.fn/private-tagged :test.fn/extra}
               (-> config keys set)))
        (is (= :test.fn/simple (protocols/name simple)))
        (is (empty? (protocols/required simple)))
        (is (= :simple-value (protocols/produce simple nil)))
        (is (empty? (-> config :test.fn/private-tagged protocols/provided-tags)))
        (is (= #{:test/extra} (-> config :test.fn/extra protocols/provided-tags))))))

  (testing "finds halt hooks"
    (with-test-ns 'test.hooks [['start '{:init/name true} (fn [] :started)]
                               ['stop '{:arglists ([x])
                                        :init/halts test.hooks/start} (fn [_] :stopped)]]
      (let [config (meta/find-components 'test.hooks)]
        (is (= [:test.hooks/start] (keys config)))
        (is (= :stopped (protocols/dispose (:test.hooks/start config) nil)))))))

;; TODO: Test :init/inject
