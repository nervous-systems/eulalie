(ns ^:no-doc eulalie.service.dynamo.request
  (:require [#?(:clj clojure.spec :cljs cljs.spec) :as s]
            [#?(:clj clojure.spec.gen :cljs cljs.spec.impl.gen) :as gen]
            [eulalie.service.impl.spec :refer [string*]]
            [clojure.string :as str]))

(defn enum [& members]
  (s/with-gen
    (into #{} members)
    (fn []
      (gen/elements members))))

(s/def ::string-like
  (s/with-gen
    #(or (string? %) (keyword? %))
    gen/keyword))

(defmulti body->spec ::target)

(s/def ::projection-expression (s/coll-of ::string-like :gen-max 3))

(s/def ::attr-name  (string* "." 1 255 {:keyword? true}))
(s/def ::attr-value string?)

(s/def ::B    string?)
(s/def ::BOOL boolean?)

(s/def ::N    string?)
(s/def ::NULL boolean?)
(s/def ::S    string?)

(s/def ::BS   (s/coll-of ::B :gen-max 5))
(s/def ::NS   (s/coll-of ::N :gen-max 5))
(s/def ::SS   (s/coll-of ::S :gen-max 5))

(s/def ::L    (s/coll-of        ::attr :gen-max 5))
(s/def ::M    (s/map-of string? ::attr :gen-max 5))

(s/def :eulalie.service.dynamo.request.attr/B    (s/map-of #{:B}    ::B    :count 1))
(s/def :eulalie.service.dynamo.request.attr/BS   (s/map-of #{:BS}   ::BS   :count 1))
(s/def :eulalie.service.dynamo.request.attr/L    (s/map-of #{:L}    ::L    :count 1))
(s/def :eulalie.service.dynamo.request.attr/M    (s/map-of #{:M}    ::M    :count 1))
(s/def :eulalie.service.dynamo.request.attr/N    (s/map-of #{:N}    ::N    :count 1))
(s/def :eulalie.service.dynamo.request.attr/NS   (s/map-of #{:NS}   ::NS   :count 1))
(s/def :eulalie.service.dynamo.request.attr/BOOL (s/map-of #{:BOOL} ::BOOL :count 1))
(s/def :eulalie.service.dynamo.request.attr/NULL (s/map-of #{:NULL} ::NULL :count 1))
(s/def :eulalie.service.dynamo.request.attr/S    (s/map-of #{:S}    ::S    :count 1))
(s/def :eulalie.service.dynamo.request.attr/SS   (s/map-of #{:SS}   ::SS   :count 1))

(s/def :eulalie.service.dynamo.request.attr/scalar
  (s/or :B    :eulalie.service.dynamo.request.attr/B
        :N    :eulalie.service.dynamo.request.attr/N
        :BOOL :eulalie.service.dynamo.request.attr/BOOL
        :NULL :eulalie.service.dynamo.request.attr/NULL
        :S    :eulalie.service.dynamo.request.attr/S))

(s/def :eulalie.service.dynamo.request.attr/seq
  (s/or :BS   :eulalie.service.dynamo.request.attr/BS
        :L    :eulalie.service.dynamo.request.attr/L
        :NS   :eulalie.service.dynamo.request.attr/NS
        :SS   :eulalie.service.dynamo.request.attr/SS))

(s/def ::attr
  (s/or :scalar :eulalie.service.dynamo.request.attr/scalar
        :seq    :eulalie.service.dynamo.request.attr/seq))

(s/def ::key-map (s/map-of ::attr-name ::attr :min-count 1 :max-count 2))
(s/def ::keys    (s/coll-of ::key-map :min-count 1 :max-count 100 :gen-max 5))

(s/def ::consistent-read boolean?)
(s/def ::expression-attribute-names (s/map-of ::string-like ::attr-name :gen-max 3))

(s/def ::keys-and-attrs
  (s/keys :req-un [::keys]
          :opt-un [::consistent-read
                   ::expression-attribute-names
                   ::projection-expression]))

(s/def ::table-name (string* "[a-z0-9_.-]" 3 255 {:keyword? true}))

(s/def :eulalie.service.dynamo.request.batch-get-item/request-items
  (s/map-of ::table-name ::keys-and-attrs :min-count 1 :gen-max 5))
(s/def ::return-consumed-capacity (enum :INDEXES :TOTAL :NONE))

(s/def :eulalie.service.dynamo.request.target/batch-get-item
  (s/keys :req-un [:eulalie.service.dynamo.request.batch-get-item/request-items]
          :opt-un [::return-consumed-capacity]))
(defmethod body->spec :batch-get-item [_]
  :eulalie.service.dynamo.request.target/batch-get-item)

(s/def ::return-item-collection-metrics (enum :SIZE :NONE))

(s/def :eulalie.service.dynamo.request.batch-write-item/key  ::key-map)
(s/def :eulalie.service.dynamo.request.batch-write-item/item ::key-map)

(s/def ::delete-request
  (s/keys :req-un [:eulalie.service.dynamo.request.batch-write-item/key]))
(s/def ::put-request
  (s/keys :req-un [:eulalie.service.dynamo.request.batch-write-item/item]))

(s/def ::write-request
  (s/keys :req-un [(or ::delete-request ::put-request)]))
(s/def ::write-requests
  (s/coll-of ::write-request :min-count 1 :max-count 25 :gen-max 5))
(s/def :eulalie.service.dynamo.request.batch-write-item/request-items
  (s/map-of ::table-name ::write-requests :gen-max 5))

(s/def :eulalie.service.dynamo.request.target/batch-write-item
  (s/keys :req-un [:eulalie.service.dynamo.request.batch-write-item/request-items]
          :opt-un [::return-consumed-capacity
                   ::return-item-collection-metrics]))
(defmethod body->spec :batch-write-item [_]
  :eulalie.service.dynamo.request.target/batch-write-item)

(s/def ::attribute-name ::attr-name)
(s/def ::attribute-type (enum :S :N :B))
(s/def ::attribute-definition
  (s/keys :req-un [::attribute-name ::attribute-type]))
(s/def ::attribute-definitions
  (s/coll-of ::attribute-definition :min-count 1 :gen-max 3))

(s/def ::key-type (enum :HASH :RANGE))
(s/def ::key-schema-element
  (s/keys :req-un [::attribute-name ::key-type]))
(s/def ::key-schema (s/coll-of ::key-schema-element :min-count 1 :max-count 2))

(s/def ::capacity-units
  (s/int-in 1 #?(:clj Integer/MAX_VALUE :cljs js.Number.MAX_SAFE_INTEGER)))
(s/def ::read-capacity-units  ::capacity-units)
(s/def ::write-capacity-units ::capacity-units)
(s/def ::provisioned-throughput
  (s/keys :req-un [::read-capacity-units ::write-capacity-units]))

(s/def ::index-name (string* "[a-z0-9_.-]" 3 255 {:keyword? true}))

(s/def ::non-key-attributes
  (s/coll-of ::attr-name :min-count 1 :max-count 20 :gen-max 3))
(s/def ::projection-type (enum :ALL :KEYS_ONLY :INCLUDE))
(s/def ::projection      (s/keys :opt-un [::non-key-attributes ::projection-type]))
(s/def ::global-secondary-index
  (s/keys :req-un [::index-name ::key-schema ::projection ::provisioned-throughput]))

(s/def ::global-secondary-indexes (s/coll-of ::global-secondary-index :max-count 5))
(s/def ::local-secondary-indexes  (s/coll-of ::local-secondary-index :max-count 5))

(s/def ::local-secondary-index
  (s/keys ::req-un [::index-name ::key-schema ::projection]))

(s/def ::stream-enabled   boolean?)
(s/def ::stream-view-type (enum :KEYS_ONLY :NEW_IMAGE :OLD_IMAGE :NEW_AND_OLD_IMAGES))
(s/def ::stream-specification
  (s/keys :opt-un [::stream-enabled ::stream-view-type]))

(s/def :eulalie.service.dynamo.request.target/create-table
  (s/keys :req-un [::attribute-definitions
                   ::key-schema
                   ::provisioned-throughput
                   ::table-name]
          :opt-un [::global-secondary-indexes
                   ::local-secondary-indexes
                   ::stream-specification]))
(defmethod body->spec :create-table [_]
  :eulalie.service.dynamo.request.target/create-table)

(s/def ::condition-expression string?)

(s/def ::key ::key-map)
(s/def :eulalie.service.dynamo.request.delete-item/return-values
  (enum :NONE :ALL_OLD))
(s/def ::expression-attribute-values (s/map-of ::string-like ::attr :gen-max 3))
(s/def ::condition-expression string?)

(s/def ::write-opts
  (s/keys :opt-un [::expression-attribute-names
                   ::expression-attribute-values
                   ::return-consumed-capacity]))

(s/def :eulalie.service.dynamo.request.target/delete-item
  (-> (s/keys :req-un [::key ::table-name]
              :opt-un [::condition-expression
                       ::return-item-collection-metrics
                       :eulalie.service.dynamo.request.delete-item/return-values])
      (s/merge ::write-opts)))

(defmethod body->spec :delete-item [_]
  :eulalie.service.dynamo.request.target/delete-item)

(s/def :eulalie.service.dynamo.request.target/delete-table
  (s/keys :req-un [::table-name]))

(defmethod body->spec :delete-table [_]
  :eulalie.service.dynamo.request.target/delete-table)

(s/def :eulalie.service.dynamo.request.target/describe-limits
  (s/with-gen (s/nilable map?)
    #(gen/return nil)))

(defmethod body->spec :describe-limits [_]
  :eulalie.service.dynamo.request.target/describe-limits)

(s/def :eulalie.service.dynamo.request.target/describe-table
  (s/keys :req-un [::table-name]))

(defmethod body->spec :describe-table [_]
  :eulalie.service.dynamo.request.target/describe-table)

(s/def :eulalie.service.dynamo.request.target/get-item
  (s/keys :req-un [::key ::table-name]
          :opt-un [::consistent-read
                   ::expression-attribute-names
                   ::projection-expression
                   ::return-consumed-capacity]))

(defmethod body->spec :get-item [_]
  :eulalie.service.dynamo.request.target/get-item)

(s/def ::exclusive-start-table-name ::table-name)
(s/def ::limit                      (s/int-in 1 100))

(s/def :eulalie.service.dynamo.request.target/list-tables
  (-> (s/keys :opt-un [::exclusive-start-table-name ::limit])
      s/nilable))

(defmethod body->spec :list-tables [_]
  :eulalie.service.dynamo.request.target/list-tables)

(s/def :eulalie.service.dynamo.request.put-item/return-values (enum :NONE :ALL_OLD))
(s/def ::item (s/map-of ::attr-name ::attr :gen-max 3))

(s/def :eulalie.service.dynamo.request.target/put-item
  (-> (s/keys :req-un [::item ::table-name]
              :opt-un [::condition-expression
                       ::return-item-collection-metrics
                       :eulalie.service.dynamo.request.put-item/return-values])
      (s/merge ::write-opts)))

(defmethod body->spec :put-item [_]
  :eulalie.service.dynamo.request.target/put-item)

(s/def ::scan-index-forward       boolean?)
(s/def ::key-condition-expression string?)
(s/def ::filter-expression        string?)
(s/def ::exclusive-start-key      ::key-map)
(s/def ::select
  (enum :ALL_ATTRIBUTES :SPECIFIC_ATTRIBUTES :COUNT :ALL_PROJECTED_ATTRIBUTES))

(s/def ::query-like-opts
  (-> (s/keys :opt-un [::consistent-read
                       ::exclusive-start-key
                       ::expression-attribute-names
                       ::expression-attribute-values
                       ::filter-expression
                       ::projection-expression
                       ::index-name
                       ::limit
                       ::select
                       ::return-consumed-capacity])
      (s/merge ::write-opts)))

(s/def :eulalie.service.dynamo.request.target/query
  (-> (s/keys :req-un [::table-name]
              :opt-un [::key-condition-expression
                       ::scan-index-forward])
      (s/merge ::query-like-opts)))

(defmethod body->spec :query [_]
  :eulalie.service.dynamo.request.target/query)

(s/def ::segment        (s/int-in 0 999999))
(s/def ::total-segments (s/int-in 1 1000000))

(s/def :eulalie.service.dynamo.request.target/scan
  (-> (s/keys :req-un [::table-name]
              :opt-un [::segment ::select ::total-segments])
      (s/merge ::query-like-opts)))

(defmethod body->spec :scan [_]
  :eulalie.service.dynamo.request.target/scan)

(s/def :eulalie.service.dynamo.request.update-item/return-values
  (enum :NONE :ALL_OLD :UPDATED_OLD :ALL_NEW :UPDATED_NEW))
(s/def ::update-expression string?)

(s/def :eulalie.service.dynamo.request.target/update-item
  (-> (s/keys :req-un [::table-name ::key]
              :opt-un [::condition-expression
                       ::return-item-collection-metrics
                       :eulalie.service.dynamo.request.update-item/return-values
                       ::update-expression])
      (s/merge ::write-opts)))

(defmethod body->spec :update-item [_]
  :eulalie.service.dynamo.request.target/update-item)

(s/def ::create ::global-secondary-index)
(s/def ::delete (s/keys :req-un [::index-name]))
(s/def ::update (s/keys :req-un [::index-name ::provisioned-throughput]))

(s/def ::global-secondary-index-update
  (s/or :create (s/keys :req-un [::create])
        :update (s/keys :req-un [::update])
        :delete (s/keys :req-un [::delete])))

(s/def ::global-secondary-index-updates
  (s/coll-of ::global-secondary-index-update :gen-max 3))

(s/def :eulalie.service.dynamo.request.target/update-table
  (s/keys :req-un [::table-name]
          :opt-un [::attribute-definitions
                   ::global-secondary-index-updates
                   ::provisioned-throughput
                   ::stream-specification]))

(defmethod body->spec :update-table [_]
  :eulalie.service.dynamo.request.target/update-table)

(s/def :eulalie.service.dynamo/request
  (s/multi-spec body->spec ::target))
