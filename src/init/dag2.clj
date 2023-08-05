(ns init.dag2
  (:require [clojure.string :as str]))

(defn- distance [graph a b]
  (get-in graph [a b] 0))

(defn depends?
  "Returns true if `a` directly or transitively depends on `b`"
  [graph a b]
  (pos? (distance graph a b)))

(defn- assign [graph a b dist]
  (-> graph
      (assoc-in [a b] dist)
      (assoc-in [b a] (- dist))))

(defn- assign-min [graph a b dist]
  (cond-> graph
    (< dist (get-in graph [a b] Long/MAX_VALUE))
    (assign a b dist)))

;; Updates the dependents of `a`, now that `a` depends on `b`.
(defn- update-dependents [graph a b delta]
  (reduce-kv (fn [g x d']
               (cond-> g
                 (neg? d') ;; x->a with distance -d'
                 (assign-min x b (- delta d')))) ;; -d + delta
             graph
             (get graph a)))

;; Updates the dependencies of `b`, now that `a` depends on `b`.
(defn- update-dependencies [graph a b delta]
  (reduce-kv (fn [g x d]
               (cond-> g
                 (pos? d) ;; b->x with distance d
                 (-> (assign-min a x (+ d delta))
                     (update-dependents a x (+ d delta)))))
             graph
             (get graph b)))

;; Maintains a sparse, signed distance matrix.
;; If the distance (get-in graph [a b])...
;; - does not exist / 0: no path a->b
;; - pos?: a->b, a depends on b
;; - neg?: b->a, b depends on a
;; currently there is no removal (not a use case for init)
;; fast: transitive lookup / expansion
;; slow-ish: immediate-deps
;; hope: get to a stable topo-sort
(defn depend [graph a b]
  (let [dist (distance graph a b)]
    (cond
      (neg? dist) (throw (ex-info "cycle" {:rel [a b]}))
      (= 1 dist)  graph ;; up-to-date
      :else (let [delta (- 1 dist)]
              (-> graph
                  (assign a b 1)
                  (update-dependents a b delta)
                  (update-dependencies a b delta))))))

(defn immediate-dependencies [graph]
  (mapcat (fn [[a dist]]
            (keep (fn [[b d]]
                    (when (= 1 d)
                      [a b]))
                  dist))
          graph))

(defn toposort
  ([graph]
   (toposort graph (keys graph)))
  ([graph nodes]
   ;; This does not work, as `distance` is not a total ordering!
   (sort (partial distance graph) nodes)))

(defn mermaid
  "Formats `graph` to mermaid flowchart syntax."
  [graph]
  (->> (immediate-dependencies graph)
       (map (fn [[a b]] (str "    " a " --> " b)))
       (concat ["flowchart TB"])
       (str/join "\n")))

(comment
  (def graph (-> {}
                 (depend :g :a)
                 (depend :c :a)
                 (depend :d :b)
                 (depend :e :b)
                 (depend :f :c)
                 (depend :g :e)
                 (depend :b :a)))

  graph
  (print (mermaid graph))

  (toposort graph)
  (toposort graph (reverse (sort (keys graph))))
  (toposort graph [:f :e :d :b :a])


  (defn- build [deps]
    (reduce (fn [g [a b]] (depend g a b)) {} deps))

  (let [deps [[:b :a] [:c :a] [:d :b] [:e :b] [:f :c]]
        graph (build deps)]
    (= graph (build (shuffle deps)))))
