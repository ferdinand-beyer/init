(ns init.meta-test.disposes-not-in-config)

(defn dispose
  {:init/disposes ::not-in-config}
  [_])
