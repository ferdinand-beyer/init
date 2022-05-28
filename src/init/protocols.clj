(ns init.protocols
  (:refer-clojure :only [defprotocol]))

(defprotocol Selector
  (tags [selector]))

(defprotocol Dependency
  (unique? [dependency]))

(defprotocol Dependent
  (required [dependent]))

(defprotocol Component
  (name [component])
  (provided-tags [component]))

(defprotocol Producer
  (produce [producer args]))

;; TODO: Better name?
(defprotocol Disposer
  (dispose [disposer instance]))

;; TODO: Add protocols for config, graph, system as well?
;; Graph could also implement config, system could be implemented on top
;; of abstractions (plug in other config or graph implementations)
