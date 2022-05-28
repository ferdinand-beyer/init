(ns init.specs
  (:require [clojure.spec.alpha :as s]))

(s/def ::name qualified-ident?)

(s/def ::tag (s/or :keyword qualified-keyword?
                   :symbol  qualified-symbol?
                   :class   class?))

(s/def ::ref (s/or :name   ::name
                   :symbol symbol?
                   :var    var?))

(s/def ::hook (s/or :fn     fn?
                    :symbol symbol?
                    :var    var?))

;;;; injection points

(s/def ::selector (s/+ ::tag))

(s/def ::unique-clause (s/cat :clause   #{:unique}
                              :selector ::selector))

(s/def ::set-clause (s/cat :clause   #{:set}
                           :selector ::selector))

(s/def ::map-clause (s/cat :clause #{:map}
                           :keys   (s/+ ::tag)))

(s/def ::injection-point-clause (s/or :unique ::unique-clause
                                      :set    ::set-clause
                                      :map    ::map-clause))

(s/def ::injection-point-map (s/map-of keyword? ::injection-point
                                       :min-count 1))

(s/def ::injection-point (s/or :tag      ::tag
                               :clause   ::injection-point-clause
                               :selector ::selector
                               :set      (s/coll-of ::tag :kind set? :min-count 1)
                               :map      ::injection-point-map))

;;;; inject

(s/def ::inject-partial (s/cat :clause #{:partial}
                               :body   (s/* ::injection-point)))

(s/def ::inject-into (s/cat :clause #{:into-first :into-last}
                            :body   (s/alt :clause ::map-clause
                                           :map    ::injection-point-map
                                           :keys   (s/+ ::tag))))

(s/def ::inject-clause (s/or :partial ::inject-partial
                             :into    ::inject-into))

(s/def ::inject-args (s/coll-of ::injection-point))

(s/def ::inject (s/or :tagged true?
                      :clause ::inject-clause
                      :args   ::inject-args))

;;;; meta

(s/def :init/name (s/or :tagged true?
                        :name   ::name))

(s/def :init/provides (s/or :single   ::tag
                            :multiple (s/coll-of ::tag)))

(s/def :init/inject ::inject)

(s/def :init/halt-fn ::hook)
(s/def :init/halts ::ref)

(s/def ::component-meta (s/keys :opt [:init/name
                                      :init/provides
                                      :init/inject
                                      :init/halt-fn]))

(s/def ::hook-meta (s/keys :opt [:init/halts]))

(s/def ::meta (s/merge ::component-meta ::hook-meta))
