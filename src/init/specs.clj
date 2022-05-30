(ns init.specs
  (:require [clojure.spec.alpha :as s]))

(s/def ::name qualified-ident?)

(s/def ::tag
  (s/or :keyword qualified-keyword?
        :symbol  qualified-symbol?
        :class   class?))

(s/def ::ref
  (s/or :name   ::name
        :symbol symbol?
        :var    var?))

(s/def ::hook
  (s/or :fn     fn?
        :symbol symbol?
        :var    var?))

;;;; injected values

(s/def ::inject-set
  (s/coll-of ::tag
             :kind set?
             :min-count 1))

(s/def ::inject-keys
  (s/cat :clause #{:keys}
         :keys   (s/+ ::tag)))

(s/def ::inject-map
  (s/map-of keyword? ::inject-val
            :min-count 1))

(s/def ::inject-get
  (s/cat :clause #{:get}
         :val    ::inject-val
         :path   (s/+ keyword?)))

(s/def ::inject-apply
  (s/cat :clause #{:apply}
         :fn     ifn?
         :args   (s/* ::inject-val)))

(s/def ::inject-val
  (s/or :tag      ::tag
        :selector (s/+ ::tag)
        :set      ::inject-set
        :keys     ::inject-keys
        :map      ::inject-map
        :get      ::inject-get
        :apply    ::inject-apply))

;;;; inject

(s/def ::inject-partial
  (s/cat :clause #{:partial}
         :vals   (s/* ::inject-val)))

;; TODO: Just take one or more values and reduce them with (into)
(s/def ::inject-into
  (s/cat :clause #{:into-first :into-last}
         :val    (s/alt :keys   (s/+ ::tag)
                        :map    ::inject-map)))

(s/def ::inject
  (s/or :tagged  true?
        :vals    (s/* ::inject-val)
        :partial ::inject-partial
        :into    ::inject-into))

;;;; meta

(s/def :init/name
  (s/or :tagged true?
        :name   ::name))

(s/def :init/provides
  (s/or :single   ::tag
        :multiple (s/coll-of ::tag)))

(s/def :init/inject ::inject)
(s/def :init/disposer ::hook)
(s/def :init/disposes ::ref)

(s/def ::component-meta
  (s/keys :opt [:init/name
                :init/provides
                :init/inject
                :init/disposer]))

(s/def ::hook-meta
  (s/keys :opt [:init/disposes]))

(s/def ::meta
  (s/or :component ::component-meta
        :hook      ::hook-meta))
