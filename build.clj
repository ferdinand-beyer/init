(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.string :as str]
            [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]))

(def lib 'com.fbeyer/init)
(def base-version "0.1")

(defn- git [& args]
  (let [{:keys [exit out]}
        (b/process {:command-args (into ["git"] args)
                    :dir "."
                    :out :capture
                    :err :ignore})]
    (when (and (zero? exit) out)
      (str/trim-newline out))))

(defn- git-tag []
  (git "describe" "--tags" "--exact-match"))

(def version (if-let [tag (git-tag)]
               (str/replace tag #"^v" "")
               (format "%s.%s-%s" base-version (b/git-count-revs nil)
                       (if (System/getenv "CI") "ci" "dev"))))

(defn tag [_]
  (let [tag (format "v%s.%s" base-version (b/git-count-revs nil))]
    (git "tag" tag)
    (println "Tagged" tag)))

(defn clean "Clean the target directory." [opts]
  (-> opts
      (bb/clean)))

(defn test "Run the tests." [opts]
  (-> opts
      (assoc :aliases [:test/run])
      (bb/run-tests)))

(defn jar "Build the Jar." [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/clean)
      (bb/jar)))

(defn ci "Run the CI pipeline of tests (and build the Jar)." [opts]
  (-> opts
      (test)
      (jar)))

(defn install "Install the JAR locally." [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/install)))

(defn deploy "Deploy the JAR to Clojars." [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/deploy)))
