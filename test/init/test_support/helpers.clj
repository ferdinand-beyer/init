(ns init.test-support.helpers)

(defn ex-info? [ex]
  (instance? clojure.lang.ExceptionInfo ex))

(defmacro thrown
  "Runs `body` in try-catch and returns the thrown exception."
  [& body]
  `(try
     ~@body
     nil
     (catch Exception e#
       e#)))
