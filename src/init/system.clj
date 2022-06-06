(ns init.system
  (:require [init.component :as component]
            [init.graph :as graph]
            [init.errors :as errors]))

;; TODO: Add hooks to report starting/stopping components, e.g. for logging

(defn- stop-component [component value exception]
  (try
    (component/stop component value)
    exception
    (catch Exception e
      (let [wrapped (errors/stop-failed-exception component value e)]
        (if exception
          (doto exception (.addSuppressed wrapped))
          wrapped)))))

(defn- stop-system
  ([system graph selectors]
   (stop-system system graph selectors nil))
  ([system graph selectors exception]
   (reduce (fn [ex [k c]]
             (stop-component c (system k) ex))
           exception
           (graph/reverse-dependency-order graph selectors))))

(defn- start-component
  [system graph name component]
  (let [inputs (->> (graph/required-keys graph name)
                    (map (partial map system)))]
    (try
      (assoc system name (component/start component inputs))
      (catch Exception ex
        (->> (errors/start-failed-exception component ex)
             (stop-system system graph (keys system))
             throw)))))

;; We could keep an atom of the current system and support a special
;; component protocol for system-aware components.  Those could receive
;; the atom or a readonly view on it, and can then dereference the
;; system at any time, allowing runtime inspection.

(defn start
  "Starts a system from a dependency graph."
  [graph selectors]
  (-> (reduce (fn [system [k c]]
                (start-component system graph k c))
              {}
              (graph/dependency-order graph selectors))
      (with-meta {::graph graph})))

(defn stop
  "Stops a system map."
  [system selectors]
  (let [graph (-> system meta ::graph)]
    (when-let [ex (stop-system system graph selectors)]
      (throw ex))))
