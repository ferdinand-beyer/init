(ns init.discovery
  (:require [clojure.string :as str]
            [init.config :as config]
            [init.component :as component]
            [init.meta :as meta])
  (:import [java.net JarURLConnection URL]
           [java.nio.file
            FileSystem
            FileSystems
            FileVisitResult
            FileVisitor
            Files
            Path
            Paths]))

(set! *warn-on-reflection* true)

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

(defn- filename [^Path p]
  (.. p getFileName toString))

(defn- namespace-unmunge [n]
  (.replace (str n) \_ \-))

(defn- scan-dir [^Path start]
  (let [names (volatile! ())
        found (volatile! ())]
    (Files/walkFileTree
     start
     (reify FileVisitor
       (preVisitDirectory [_ dir _attrs]
         (let [n (filename dir)]
           (if (re-matches #"\w+" n)
             (do
               (when (not= start dir)
                 (vswap! names conj (namespace-unmunge n)))
               FileVisitResult/CONTINUE)
             FileVisitResult/SKIP_SUBTREE)))
       (postVisitDirectory [_ dir _exc]
         (when (not= start dir)
           (vswap! names pop))
         FileVisitResult/CONTINUE)
       (visitFile [_ path _attrs]
         (when-let [[_ n] (re-matches #"(\w+)(__init\.class|\.cljc?)" (filename path))]
           (->> (conj @names (namespace-unmunge n))
                reverse
                (str/join ".")
                (vswap! found conj)))
         FileVisitResult/CONTINUE)
       (visitFileFailed [_ path exc]
         (throw (ex-info (str "Failed to read " path) {:path path} exc)))))
    @found))

(defn- split-jar-url [^URL url]
  (let [^JarURLConnection conn (.openConnection url)]
    [(Paths/get (.. conn getJarFileURL toURI)) (.getEntryName conn)]))

(defn- jar-file-system ^FileSystem [^Path path]
  (let [^ClassLoader cl nil]
    (FileSystems/newFileSystem path cl)))

(defn- scan-jar [url]
  (let [[jar entry] (split-jar-url url)]
    (with-open [fs (jar-file-system jar)]
      (scan-dir (.getPath fs entry (into-array String nil))))))

(defn- scan-url [^URL url]
  (case (.getProtocol url)
    "file" (scan-dir (Paths/get (.toURI url)))
    "jar"  (scan-jar url)))

(defn- scan-classpath
  ([^ClassLoader cl]
   (->> (.getResources cl "")
        enumeration-seq
        (mapcat scan-url)
        (map symbol)))
  ([^ClassLoader cl prefix]
   (->> (str/replace (namespace-munge prefix) \. \/)
        (.getResources cl)
        enumeration-seq
        (mapcat scan-url)
        (map #(symbol (str prefix "." %))))))

(defn- context-classloader []
  (.. Thread currentThread getContextClassLoader))

(defn classpath-namespaces
  "Returns a set of symbols of all namespaces on the classpath.  When
   `prefixes` are given, only returns namespaces starting with one of the
   prefixes."
  ([]
   (into #{} (scan-classpath (context-classloader))))
  ([prefixes]
   (let [cl (context-classloader)]
     (into #{} (mapcat (partial scan-classpath cl)) prefixes))))

(defn load-namespaces
  "Loads and returns all namespaces matching `prefixes` on the classpath."
  [prefixes]
  (let [ns-names (classpath-namespaces prefixes)]
    (run! require ns-names)
    (map the-ns ns-names)))

(defn scan
  "Scans the classpath for namespaces starting with `prefixes`, and returns
   a config with all components found in those."
  ([prefixes]
   (from-namespaces (load-namespaces prefixes)))
  ([config prefixes]
   (from-namespaces config (load-namespaces prefixes))))

(defn- emit-static-scan [prefixes]
  (let [namespaces (classpath-namespaces prefixes)]
    `(do
       (require ~@(map (partial list 'quote) namespaces))
       (discovery/from-namespaces
        ~(mapv (fn [n] `(the-ns (quote ~n))) namespaces)))))

(defmacro static-scan
  "Like [[scan]], but scans the classpath at macro expansion time.

   Expands to code that uses `require` to load required namespaces, and a
   config map created with [[from-namespaces]]."
  [prefixes]
  (emit-static-scan (eval prefixes)))

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
