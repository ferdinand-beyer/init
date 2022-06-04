(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'init/todo-app)
(def version (format "1.0.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis-opts {:project "deps.edn"})
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)

  (println "Compiling Clojure code...")
  ;; Copy only app namespaces, with the :compile alias enabled.
  (b/compile-clj {:basis (b/create-basis (assoc basis-opts :aliases [:compile]))
                  :src-dirs ["src"]
                  :class-dir class-dir
                  :filter-nses '[todo-app]})
  ;; Now copy everything, without :compile alias.  Will not recompile app namespaces.
  (b/compile-clj {:basis (b/create-basis basis-opts)
                  :src-dirs ["src"]
                  :class-dir class-dir})

  (println "Copying resources...")
  (b/copy-dir {:src-dirs ["resources"]
               :target-dir class-dir})

  (println "Building uberjar...")
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis (b/create-basis basis-opts)
           :main 'todo-app.main}))
