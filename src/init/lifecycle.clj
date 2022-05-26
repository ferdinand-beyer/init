(ns init.lifecycle)

(defprotocol Init
  (-init [component deps]))

(defprotocol Halt
  (-halt [component instance]))
