(ns init.meta-test
  (:require [clojure.test :refer [are deftest is testing]]
            [init.meta :as meta]
            [init.meta-test.disposes-not-in-config :as disposes-not-in-config]
            [init.meta-test.disposes-unresolveable :as disposes-unresolveable]
            [init.protocols :as protocols]
            [init.test-support.helpers :refer [ex-info? thrown]]))

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
  {:init/disposer disposer}
  []
  ::disposer-fn)

(defn disposer-var-component
  {:init/disposer #'disposer}
  []
  ::disposer-var)

(defn disposer-symbol-component
  {:init/disposer 'disposer}
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

(defn ^:init/inject disposes-var-component [])

(defn disposes-var
  {:init/disposes #'disposes-var-component}
  [x]
  (disposer x))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn ^:init/inject disposes-name-component [])

(defn disposes-name
  {:init/disposes ::disposes-name-component}
  [x]
  (disposer x))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn ^:init/inject disposes-symbol-component [])

(defn disposes-symbol
  {:init/disposes 'disposes-symbol-component}
  [x]
  (disposer x))

(defn ns-of [var]
  (-> var meta :ns))

(deftest namespace-config-test
  (let [config (meta/namespace-config (ns-of #'simple-component))]

    (testing "finds tagged components"
      (is (contains? config ::simple-component) "tagged with :init/name")
      (is (contains? config ::named))
      (is (not (contains? config ::explicitly-named-component)) "using explicit name")
      (is (contains? config ::providing-component) "tagged with :init/provides")
      (is (contains? config ::producer-component) "tagged with :init/inject")
      (is (not (contains? config ::implicitly-named-component)) "not tagged")
      (is (not (contains? config ::disposes-var)) "tagged as disposer"))

    (testing "indirect disposers"
      (are [name disposer] (= (disposer name) (protocols/dispose (config name) name))
        ::disposes-var-component disposes-var
        ::disposes-name-component disposes-name
        ::disposes-symbol-component disposes-symbol)))

  (testing "definition errors"
    (let [ex (thrown (meta/namespace-config (ns-of #'disposes-not-in-config/dispose)))]
      (is (ex-info? ex))
      (is (= :init.errors/component-not-found (-> ex ex-data :reason))))

    (let [ex (thrown (meta/namespace-config (ns-of #'disposes-unresolveable/dispose)))]
      (is (ex-info? ex))
      (is (= :init.errors/component-not-found (-> ex ex-data :reason))))))
