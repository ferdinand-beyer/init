(ns init.core
  (:require [init.protocols :as protocols]))

(extend-protocol protocols/Dependency
  clojure.lang.Keyword
  (-tag [kw] kw)
  (-cardinality [_] :one)

  clojure.lang.IPersistentMap
  (-tag [m] (:tag m))
  (-cardinality [m] (:cardinality m :one)))
