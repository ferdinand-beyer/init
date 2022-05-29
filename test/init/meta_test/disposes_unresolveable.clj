(ns init.meta-test.disposes-unresolveable)

(defn dispose
  {:init/disposes 'unresolveable}
  [_])
