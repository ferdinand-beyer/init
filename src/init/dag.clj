(ns init.dag
  "Directed Acyclic Graph (DAG)")

(def empty-dag
  "An empty immutable DAG."
  [{} {}])

(def ^:private set-conj (fnil conj #{}))

(defn nodes [dag]
  (into #{} (mapcat keys) dag))

(defn add-edge [[out in] from to]
  [(update out from set-conj to)
   (update in to set-conj from)])

(defn remove-edge [[out in] from to]
  [(update out from disj to)
   (update in to disj from)])

(defn has-edges? [dag]
  (boolean (some (comp seq val) (first dag))))

(defn out-nodes
  "Returns all nodes that have edges from `node`."
  [dag node]
  (get (first dag) node))

(defn in-nodes
  "Returns all nodes that have edges to `node`."
  [dag node]
  (get (second dag) node))

(defn- expand
  [neighbors node-set]
  (loop [expanded #{}
         todo     (mapcat neighbors node-set)]
    (if-let [[node & more] (seq todo)]
      (if (contains? expanded node)
        (recur expanded more)
        (recur (conj expanded node)
               (concat more (neighbors node))))
      expanded)))

(defn expand-out
  "Transitively expand nodes from `node-set` in the DAG."
  [dag node-set]
  (expand (first dag) node-set))

(defn expand-in
  "Transitively expand nodes from `node-set` in the DAG, following
   edges in reverse direction."
  [dag node-set]
  (expand (second dag) node-set))

(defn reachable? [dag from to]
  (contains? (expand-out dag #{from}) to))

(defn find-cycles [dag]
  (letfn [(dfs [{:keys [visited? finished?] :as acc} path node]
            (if (finished? node)
              acc
              (let [path (conj path node)]
                (if (visited? node)
                  (update acc :cycles conj (drop-while #(not= node %) path))
                  (-> (reduce (fn [acc n]
                                (dfs acc path n))
                              (update acc :visited? conj node)
                              (out-nodes dag node))
                      (update :finished? conj node))))))]
    (:cycles (reduce (fn [acc node]
                       (dfs acc [] node))
                     {:cycles []
                      :visited? #{}
                      :finished? #{}}
                     (nodes dag)))))

(defn topo-order
  "Returns a map of nodes in `dag` to their 'depth' in topological order."
  [dag]
  (loop [order {}
         g     dag
         ;; Start with nodes that have no outgoing edges.
         todo  (->> (nodes dag)
                    (remove #(seq (out-nodes dag %)))
                    (map #(vector 0 %)))]
    (if (seq todo)
      (let [[i node]   (first todo)
            deps       (in-nodes g node)
            [g' todo'] (reduce (fn [[g todo] n]
                                 (let [g' (remove-edge g n node)]
                                   [g'
                                    (if (empty? (out-nodes g' n))
                                      (cons [(inc i) n] todo)
                                      todo)]))
                               [g (rest todo)]
                               deps)]
        (recur (assoc order node i) g' todo'))
      (if (has-edges? g)
        (let [cycles (find-cycles g)]
          (throw (ex-info "Cycles detected" {:reason ::cycles-detected
                                             :cycles cycles})))
        order))))

(defn topo-comparator
  "Returns a comparator to sort nodes in topological order.  Breaks ties
   using the default comparator, or `keyfn` and `cmp` when provided."
  ([dag]
   (topo-comparator dag identity))
  ([dag keyfn]
   (topo-comparator dag keyfn compare))
  ([dag keyfn cmp]
   (let [order     (topo-order dag)
         not-found (count order)]
     (fn [a b]
       (let [c (compare (get order a not-found)
                        (get order b not-found))]
         (if (zero? c)
           (cmp (keyfn a) (keyfn b))
           c))))))
