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

;;;; injection points

(s/def ::inject-val-clause
  (s/cat :clause #{:unique :set :map}
         :tags   (s/+ ::tag)))

(s/def ::inject-map
  (s/map-of keyword? ::inject-val
            :min-count 1))

(s/def ::inject-val
  (s/or :tag      ::tag
        :clause   ::inject-val-clause
        :selector (s/+ ::tag)
        :set      (s/coll-of ::tag
                             :kind set?
                             :min-count 1)
        :map      ::inject-map))

;;;; inject

(s/def ::inject-partial
  (s/cat :clause #{:partial}
         :vals   (s/* ::inject-val)))

(s/def ::inject-into
  (s/cat :clause #{:into-first :into-last}
         :val    (s/alt :clause ::map-clause
                        :map    ::inject-map
                        :keys   (s/+ ::tag))))

(s/def ::inject
  (s/or :tagged  true?
        :partial ::inject-partial
        :into    ::inject-into
        :vals    (s/* ::inject-val)))

;;;; meta

(s/def :init/name
  (s/or :tagged true?
        :name   ::name))

(s/def :init/provides
  (s/or :single   ::tag
        :multiple (s/coll-of ::tag)))

(s/def :init/inject ::inject)
(s/def :init/halt-fn ::hook)
(s/def :init/halts ::ref)

(s/def ::component-meta
  (s/keys :opt [:init/name
                :init/provides
                :init/inject
                :init/halt-fn]))

(s/def ::hook-meta
  (s/keys :opt [:init/halts]))

(s/def ::meta
  (s/or :component ::component-meta
        :hook      ::hook-meta))
