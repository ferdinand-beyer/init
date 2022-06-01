(ns init.specs
  (:require [clojure.spec.alpha :as s]))

(s/def ::name qualified-ident?)

(s/def ::tag
  (s/or :name  ::name
        :class class?))

(s/def ::tags (s/coll-of ::tag :into #{}))

(s/def ::dep
  (s/or :tag ::tag
        :seq (s/+ ::tag)
        :set (s/coll-of ::tag :kind set? :min-count 1)))

(s/def ::deps (s/* ::dep))

(s/def ::start-fn fn?)
(s/def ::stop-fn fn?)

(s/def ::component
  (s/keys :req-un [::name ::start-fn]
          :opt-un [::tags ::deps ::stop-fn]))

;;;; injected values

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
  (s/or :dep   ::dep
        :keys  ::inject-keys
        :map   ::inject-map
        :get   ::inject-get
        :apply ::inject-apply))

;;;; inject

(s/def ::inject-partial
  (s/cat :clause #{:partial}
         :vals   (s/* ::inject-val)))

(s/def ::inject-into
  (s/cat :clause #{:into-first :into-last}
         :val    ::inject-val))

(s/def ::inject
  (s/or :tagged  true?
        :vals    (s/* ::inject-val)
        :partial ::inject-partial
        :into    ::inject-into))

;;;; meta

(s/def ::meta-hook
  (s/or :fn     fn?
        :symbol symbol?
        :var    var?))

(s/def ::meta-ref
  (s/or :name   ::name
        :symbol symbol?
        :var    var?))

(s/def :init/name
  (s/or :tagged true?
        :name   ::name))

(s/def :init/tags    ::tags)
(s/def :init/inject  ::inject)
(s/def :init/stop-fn ::meta-hook)

(s/def :init/stops ::meta-ref)

(s/def ::component-meta
  (s/keys :opt [:init/name
                :init/tags
                :init/inject
                :init/stop-fn]))

(s/def ::hook-meta
  (s/keys :opt [:init/stops]))

(s/def ::meta
  (s/or :component ::component-meta
        :hook      ::hook-meta))
