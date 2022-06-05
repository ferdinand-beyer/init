(ns init.meta-test
  (:require [clojure.test :refer [are deftest is testing]]
            [init.component :as component]
            [init.meta :as meta]
            [init.meta-test.stops-not-in-config :as stops-not-in-config]
            [init.meta-test.stops-unresolveable :as stops-unresolveable]
            [init.test-support.helpers :refer [ex-info? thrown]]))

(defn ^:init/name simple-component [] ::simple)

(defn implicitly-named-component [] ::implicit-name)

(defn explicitly-named-component
  {:init/name ::named}
  []
  ::explicit-name)

(defn providing-component
  {:init/tags [::extra]}
  []
  ::provides)

(defn argvec-hinted-component
  {:init/tags [::argvec-hinted]}
  ^String []
  "A string")

#_{:clj-kondo/ignore [:non-arg-vec-return-type-hint]}
(defn ^String name-hinted-component
  {:init/tags [::name-hinted]}
  []
  "A string")

(def ^:init/name const-component ::const)

(defn producer-component
  {:init/inject [::const ::named]}
  [const named]
  [:called-with const named])

(defn partially-injected-component
  {:init/inject [:partial ::const]}
  [injected runtime-arg]
  [:called-with injected runtime-arg])

(defn stop [x] [:stopped x])

(defn stop-fn-component
  {:init/stop-fn stop}
  []
  ::stop-fn)

(defn stop-var-component
  {:init/stop-fn #'stop}
  []
  ::stop-var)

(defn stop-symbol-component
  {:init/stop-fn 'stop}
  []
  ::stop-symbol)

(deftest component-test
  (testing "get wrapped var"
    (is (= #'simple-component (:var (meta/component #'simple-component)))))

  (testing "component names"
    (is (= ::simple-component (:name (meta/component #'simple-component))))
    (is (= ::implicitly-named-component (:name (meta/component #'implicitly-named-component))))
    (is (= ::named (:name (meta/component #'explicitly-named-component)))))

  (testing "provided tags"
    (is (empty? (:tags (meta/component #'simple-component))))
    (is (= #{::extra} (:tags (meta/component #'providing-component))))
    (is (= #{String ::name-hinted} (:tags (meta/component #'name-hinted-component))))
    (is (= #{String ::argvec-hinted} (:tags (meta/component #'argvec-hinted-component)))))

  (testing "constant component"
    (let [c (meta/component #'const-component)]
      (is (empty? (:deps c)))
      (is (= const-component (component/start c nil)))
      (is (nil? (component/stop c const-component)))))

  (testing "nullary component"
    (is (= ::simple (component/start (meta/component #'simple-component) nil))))

  (testing "producer dependency injection"
    (let [c    (meta/component #'producer-component)
          deps (:deps c)]
      (is (= [::const ::named] deps))
      (is (= [:called-with :const :named] (component/start c [[:const] [:named]])))))

  (testing "partial dependency injection"
    (let [c (meta/component #'partially-injected-component)]
      (is (= [::const] (:deps c)))
      (let [f (component/start c [[:injected]])]
        (is (= [:called-with :injected :runtime] (f :runtime))))))

  (testing "stopping components"
    (are [var] (= [:stopped (var)] (component/stop (meta/component var) (var)))
      #'stop-fn-component
      #'stop-var-component
      #'stop-symbol-component)))

(defn ^:init/inject stops-var-component [])

(defn stops-var
  {:init/stops #'stops-var-component}
  [x]
  (stop x))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn ^:init/inject stops-name-component [])

(defn stops-name
  {:init/stops ::stops-name-component}
  [x]
  (stop x))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn ^:init/inject stops-symbol-component [])

(defn stops-symbol
  {:init/stops 'stops-symbol-component}
  [x]
  (stop x))

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
      (is (not (contains? config ::stops-var)) "tagged as stop"))

    (testing "indirect stops"
      (are [name stop] (= (stop name) (component/stop (config name) name))
        ::stops-var-component stops-var
        ::stops-name-component stops-name
        ::stops-symbol-component stops-symbol)))

  (testing "definition errors"
    (let [ex (thrown (meta/namespace-config (ns-of #'stops-not-in-config/stop)))]
      (is (ex-info? ex))
      (is (= :init.errors/component-not-found (-> ex ex-data :reason))))

    (let [ex (thrown (meta/namespace-config (ns-of #'stops-unresolveable/stop)))]
      (is (ex-info? ex))
      (is (= :init.errors/component-not-found (-> ex ex-data :reason))))))
