(ns init.discovery
  (:require [clojure.string :as str]
            [init.config :as config]
            [init.component :as component]
            [init.meta :as meta]))

(defn from-namespaces
  "Builds a configuration from the given namespaces."
  ([namespaces]
   (from-namespaces {} namespaces))
  ([config namespaces]
   (reduce #(config/merge-configs %1 (meta/namespace-config %2))
           config
           namespaces)))

(defn bind
  "Returns a config from all the values of `m`, coerced into components,
   and tagged with the corresponding keys of `m`.

   Designed to be used similar to how Guice or Dagger 2 define _modules_,
   binding implementations to types.  With `bind`, you can bind
   implementation vars to abstract tags."
  ([m]
   (bind {} m))
  ([config m]
   (reduce-kv #(config/add-component %1 (-> (component/component %3)
                                            (component/tag %2)))
              config
              m)))

(defn- ns-prefix-pred
  "Returns a predicate that matches namespace names (ie. symbols) by prefix."
  [prefix]
  (let [n (name prefix)
        p (str n ".")]
    (fn [ns-name]
      (let [ns (name ns-name)]
        (or (= n ns)
            (str/starts-with? ns p))))))

(defn- some-prefix-pred
  [prefixes]
  (apply some-fn (map ns-prefix-pred prefixes)))

(defn find-namespaces
  "Returns namespaces whose names start with one of the `prefixes`.  Can be
   used together with [[from-namespaces]] to build a config from loaded
   namespaces, found by `prefixes`."
  [prefixes]
  (filter (comp (some-prefix-pred prefixes) ns-name) (all-ns)))

;;;; CLASSPATH SCANNING

(defn classpath-namespaces
  "Returns a set of symbols of all namespaces on the classpath.  When
   `prefixes` are given, only returns namespaces starting with one of the
   prefixes.

   Requires `org.clojure/java.classpath` and `org.clojure/tools.namespace`
   on the classpath."
  ([]
   (let [classpath       (requiring-resolve 'clojure.java.classpath/classpath)
         find-namespaces (requiring-resolve 'clojure.tools.namespace.find/find-namespaces)]
     (set (find-namespaces (classpath)))))
  ([prefixes]
   (filter (some-prefix-pred prefixes) (classpath-namespaces))))

(defn load-namespaces
  "Loads and returns all namespaces matching `prefixes` on the classpath.

   See [[classpath-namespaces]] for required libraries."
  [prefixes]
  (let [ns-names (classpath-namespaces prefixes)]
    (run! require ns-names)
    (map the-ns ns-names)))

(defn scan
  "Scans the classpath for namespaces starting with `prefixes`, and returns
   a config with all components found in those.

   See [[classpath-namespaces]] for required libraries."
  ([prefixes]
   (from-namespaces (load-namespaces prefixes)))
  ([config prefixes]
   (from-namespaces config (load-namespaces prefixes))))

(defn- emit-config [config]
  (let [libspecs      (->> (vals config)
                           (map #(-> %1 :var meta :ns ns-name))
                           (into #{})
                           (map #(list `quote %)))
        static-config (update-vals config (fn [{:keys [var hook-vars]}]
                                            `(meta/component ~var ~hook-vars)))]
    `(do (require ~@libspecs)
         ~static-config)))

(defmacro static-scan
  "Like [[scan]], but discovers the configuration at macro expansion time.
   As such, libraries required for [[classpath-namespaces]] are only needed
   during compilation, not at runtime.

   Expands to code that uses `require` to load required namespaces, and a
   config map creating var components with [[init.meta/component]].

   Designed to be used in ahead-of-time compiled applications, that have
   `clojure.tools.namespace` as a development dependency but don't want to
   have it as a runtime dependency."
  [prefixes]
  (-> (eval prefixes) scan emit-config))

;;;; SERVICE REGISTRY

(defn services
  "Builds a configuration map from namespaces configured in
   `META-INF/services/init.namespaces` files on the classpath.  The format
   of these files is the same as defined by `java.util.ServiceLoader`.

   The default filename `init.namespaces` can be overridden with `name`.

   Requires `com.fbeyer/autoload` on the classpath."
  ([]
   (services "init.namespaces"))
  ([name]
   (let [services   (requiring-resolve 'com.fbeyer.autoload/services)
         namespaces (set (services name))]
     (run! require namespaces)
     (from-namespaces namespaces))))
