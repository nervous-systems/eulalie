(ns eulalie.dynamo.test-data)

(def all-request-keys
  {:attributes-to-get [:Xx :YYY :zzz]
   :conditional-operator :and
   :consistent-read true
   :exclusive-start-key {:exclusive-start-key {:SS {:attributes-to-get {:L [{:SS ["v"]}]}}}}
   :expression-attribute-names {:#a1 :X :#A2 :y}
   :expression-attribute-values
   {":column-one" {:NS [30]} ":column-two" {:L  [{:S "hi"}]}}
   :filter-expression "#x = 1 or #y = 2"
   :index-name :the-index-name
   :key-conditions
   {:name {:attribute-value-list [{:SS ["Joseph"]}]
           :comparison-operator :eq}}
   :limit 1
   :projection-expression [:#a1 :X]
   :query-filter {:job {:attribute-value-list [{:S "1"}]
                        :comparison-operator :begins-with}}
   :scan-filter {:scan-filter {:attribute-value-list {:M {:attribute-value-list {:S " "}}}
                               :comparison-operator :not-contains}}
   :segment 1
   :total-segments 2
   :select :all-attributes
   :table-name :the-table-name
   :scan-index-forward false
   :return-consumed-capacity :total
   :return-item-collection-metrics :size
   :attribute-definitions
   [{:attribute-name :attribute-name :attribute-type :SS}
    {:attribute-name :ATTR_name      :attribute-type :N}]
   :global-secondary-indexes
   [{:index-name :the-index-name
     :key-schema [{:attribute-name :attribute.name
                   :key-type       :hash}]
     :projection {:non-key-attributes [:a :b-c :d]
                  :projection-type :keys-only}
     :provisioned-throughput {:write-capacity-units 1
                              :read-capacity-units  2}}]
   :key-schema [{:attribute-name :xyz :key-type :range}]
   :local-secondary-indexes
   [{:index-name :local-secondary-indexNAME
     :key-schema [{:attribute-name :xyz :key-type :range}]
     :projection {:non-key-attributes [:X]
                  :projection-type :include}}]
   :condition-expression "f <= 6 AND 6 <= f"
   :expected {:expected
              {:attribute-value-list {:M {:attribute-value-list {:L [{:SS #{" "}}]}}}
               :comparison-operator :eq
               :exists false
               :value {:M {:value {:SS #{" "}}}}}}
   :key {:xxx-yyy {:M {:xxx-yyy {:M {:key {:S " "}}}}}}
   :return-values :updated-new
   :exclusive-start-table-name :exclusive-start-table-name
   :item {:item {:BOOL true}
          :BOOL {:M {:BOOL {:L {:BOOL false}}}}}
   :attribute-updates {:av {:action :put
                            :value {:M {:action {:S "put"}}}}
                       :a2 {:action :add
                            :value {:L [{:S "add"}]}}}
   :update-expression "SET a=:value1, b:=value2"
   :global-secondary-index-updates
   [{:update {:index-name :amazingINDEXname
              :provisioned-throughput {:read-capacity-units 2
                                       :write-capacity-units 3}}}]})

(def all-request-keys-out
  {:AttributesToGet [:Xx :YYY :zzz]
   :ConditionalOperator :AND
   :ConsistentRead true
   :ExclusiveStartKey
   {:exclusive-start-key {:SS {:attributes-to-get {:L [{:SS ["v"]}]}}}}
   :ExpressionAttributeNames {:#A2 :y :#a1 :X}
   :ExpressionAttributeValues
   {":column-one" {:NS [30]}, ":column-two" {:L [{:S "hi"}]}}
   :FilterExpression "#x = 1 or #y = 2"
   :IndexName :the-index-name
   :KeyConditions
   {:name
    {:ComparisonOperator :EQ :AttributeValueList [{:SS ["Joseph"]}]}}
   :Limit 1
   :ProjectionExpression "#a1,X"
   :QueryFilter
   {:job
    {:ComparisonOperator :BEGINS_WITH :AttributeValueList [{:S "1"}]}}
   :Select :ALL_ATTRIBUTES
   :TableName :the-table-name
   :ScanIndexForward false
   :ReturnConsumedCapacity :TOTAL
   :ReturnItemCollectionMetrics :SIZE
   :AttributeDefinitions
   [{:AttributeName :attribute-name :AttributeType :SS}
    {:AttributeName :ATTR_name      :AttributeType :N}]
   :GlobalSecondaryIndexes
   [{:IndexName :the-index-name
     :KeySchema [{:AttributeName :attribute.name
                  :KeyType       :HASH}]
     :Projection {:NonKeyAttributes [:a :b-c :d]
                  :ProjectionType :KEYS_ONLY}
     :ProvisionedThroughput {:WriteCapacityUnits 1
                             :ReadCapacityUnits 2}}]
   :KeySchema [{:AttributeName :xyz :KeyType :RANGE}]
   :LocalSecondaryIndexes
   [{:IndexName :local-secondary-indexNAME
     :KeySchema [{:AttributeName :xyz :KeyType :RANGE}]
     :Projection {:NonKeyAttributes [:X]
                  :ProjectionType :INCLUDE}}]
   :ConditionExpression "f <= 6 AND 6 <= f"
   :Expected {:expected
              {:AttributeValueList {:M {:attribute-value-list {:L [{:SS #{" "}}]}}}
               :ComparisonOperator :EQ
               :Exists false
               :Value {:M {:value {:SS #{" "}}}}}}
   :Key {:xxx-yyy {:M {:xxx-yyy {:M {:key {:S " "}}}}}}
   :ReturnValues :UPDATED_NEW
   :ExclusiveStartTableName :exclusive-start-table-name
   :Item {:item {:BOOL true}
          :BOOL {:M {:BOOL {:L {:BOOL false}}}}}
   :ScanFilter {:scan-filter {:AttributeValueList {:M {:attribute-value-list {:S " "}}}
                              :ComparisonOperator :NOT_CONTAINS}}
   :Segment 1
   :TotalSegments 2
   :AttributeUpdates {:av {:Action :PUT :Value {:M {:action {:S "put"}}}}
                      :a2 {:Action :ADD :Value {:L [{:S "add"}]}}}
   :UpdateExpression "SET a=:value1, b:=value2"
   :GlobalSecondaryIndexUpdates
   [{:Update {:IndexName :amazingINDEXname
              :ProvisionedThroughput {:ReadCapacityUnits 2
                                      :WriteCapacityUnits 3}}}]})

(def table-description
  {:AttributeDefinitions [{:AttributeName "attr.NAME" :AttributeType "SS"}
                          {:AttributeName "###"       :AttributeType "NS"}]
   :CreationDateTime 12345
   :GlobalSecondaryIndexes
   [{:IndexName "THEindex-name"
     :IndexSizeBytes 5
     :IndexStatus "ACTIVE"
     :ItemCount 1272
     :KeySchema [{:AttributeName "the key" :KeyType "RANGE"}
                 {:AttributeName "key two" :KeyType "HASH"}]
     :Projection {:NonKeyAttributes ["a" "b1"] :ProjectionType "INCLUDE"}
     :ProvisionedThroughput
     {:LastDecreaseDateTime 3232552
      :LastIncreaseDateTime 9999999
      :NumberOfDecreasesToday 0
      :ReadCapacityUnits 2
      :WriteCapacityUnits 1}}]
   :ItemCount 1024
   :KeySchema [{:AttributeName "the best key" :KeyType "HASH"}]
   :LocalSecondaryIndexes
   [{:IndexName "ls-index-name"
     :IndexSizeBytes 2048
     :ItemCount 1
     :KeySchema [{:AttributeName "the best key" :KeyType "HASH"}]
     :Projection {:NonKeyAttributes ["a"]
                  :ProjectionType "INCLUDE"}
     }]
   :ProvisionedThroughput
   {:LastDecreaseDateTime 3232552
    :LastIncreaseDateTime 9999999
    :NumberOfDecreasesToday 0
    :ReadCapacityUnits 2
    :WriteCapacityUnits 1}
   :TableName "really_the_best_table_name"
   :TableSizeBytes 11
   :TableStatus "ACTIVE"})

(def all-response-keys
  {:ConsumedCapacity
   {:CapacityUnits 2
    :GlobalSecondaryIndexes {:GSIndex {"CapacityUnits" 1}}
    :LocalSecondaryIndexes  {:LSIndex {"CapacityUnits" 3}}
    :Table {:CapacityUnits 4}
    :TableName "amazing-table-name"}
   :Responses {:THE_table-name
               [{:column.name {:M {:Responses {:L [{:S " "}]}}}}]}
   :UnprocessedKeys
   {:napkin-game
    {:AttributesToGet [:AttributesToGet :AttributesToGet1]
     :ConsistentRead false
     :ExpressionAttributeNames {:#x "x"}
     :Keys [{:COL {:M {:Keys {:L [{:S " "}]}}}}
            {:LOC {:L [{:M {:ConsistentRead {:BOOL true}}}]}}]
     :ProjectionExpression "x,y,z"}}
   :ItemCollectionMetrics
   {:table-name
    [{:ItemCollectionKey {:c-o-l
                          {:M {:c-o-l {:S "c-o-l"}}}}
      :SizeEstimateRangeGB [1 2]}]}
   :UnprocessedItems {:table-name
                      [{:DeleteRequest {:Key  {:col-NAME {:M {:Key {:S "Key"}}}}}}
                       {:PutRequest    {:Item {:col-name {:S " "}
                                               :col-two- {:N "15"}}}}]}  
   :TableDescription table-description
   :Table table-description
   
   :Attributes {:a*** {:M {:TableSizeBytes {:L [{:S "a***"}]}}}
                :b*** {:S "BOOL"}}
   :Item {:!!! {:L [{:M {:K {:L [{:S "L"}]}}}]}
          :___ {:SS ["X"]}}
   :LastEvaluatedTableName "TABLE_n_a_m_e"
   :TableNames ["t_able_name1" "ta_bl_ename2"]
   :Count 51
   :Items [{:COL_NAME {:S "COL_NAME"}
            :OTHER_CN {:L [{:L [{:S " "}]}]}}
           {:COL_NAME {:S "COL_NAME"}}]
   :ScannedCount 72
   :LastEvaluatedKey {:SSSS {:S "SSSS"}}})

(def table-description-in
  {:key-schema [{:attribute-name (keyword "the best key") :key-type :hash}]
   :table-size-bytes 11
   :attribute-definitions
   [{:attribute-type :SS :attribute-name :attr.NAME}
    {:attribute-type :NS :attribute-name :###}]
   :creation-date-time 12345
   :local-secondary-indexes
   [{:projection {:projection-type :include :non-key-attributes [:a]}
     :index-name :ls-index-name
     :index-size-bytes 2048
     :key-schema [{:attribute-name (keyword "the best key") :key-type :hash}]
     :item-count 1}]
   :item-count 1024
   :global-secondary-indexes
   [{:index-status :active
     :projection
     {:projection-type :include :non-key-attributes [:a :b1]}
     :index-name :THEindex-name
     :index-size-bytes 5
     :key-schema
     [{:attribute-name (keyword "the key") :key-type :range}
      {:attribute-name (keyword "key two") :key-type :hash}]
     :item-count 1272
     :provisioned-throughput
     {:write-capacity-units 1
      :number-of-decreases-today 0
      :last-decrease-date-time 3232552
      :read-capacity-units 2
      :last-increase-date-time 9999999}}]
   :table-status :active
   :table-name :really_the_best_table_name
   :provisioned-throughput
   {:write-capacity-units 1
    :number-of-decreases-today 0
    :last-decrease-date-time 3232552
    :read-capacity-units 2
    :last-increase-date-time 9999999}})

(def all-response-keys-in
  {:consumed-capacity
   {:capacity-units 2
    :global-secondary-indexes {:GSIndex {:capacity-units 1}}
    :local-secondary-indexes  {:LSIndex {:capacity-units 3}}
    :table {:capacity-units 4}
    :table-name :amazing-table-name}
   :responses {:THE_table-name
               [{:column.name {:M {:Responses {:L [{:S " "}]}}}}]}
   :unprocessed-keys
   {:napkin-game
    {:attributes-to-get [:AttributesToGet :AttributesToGet1]
     :consistent-read false
     :expression-attribute-names {:#x :x}
     :keys [{:COL {:M {:Keys {:L [{:S " "}]}}}}
            {:LOC {:L [{:M {:ConsistentRead {:BOOL true}}}]}}]
     :projection-expression [:x :y :z]}}
   :item-collection-metrics
   {:table-name
    [{:item-collection-key {:c-o-l {:M {:c-o-l {:S "c-o-l"}}}}
      :size-estimate-range-gb [1 2]}]}
   :unprocessed-items
   {:table-name [{:delete-request {:key  {:col-NAME {:M {:Key {:S "Key"}}}}}}
                 {:put-request    {:item {:col-two- {:N "15"}, :col-name {:S " "}}}}]}
   :table-description table-description-in
   :table table-description-in
   
   :attributes {:b*** {:S "BOOL"}
                :a*** {:M {:TableSizeBytes {:L [{:S "a***"}]}}}}
   :item {:!!! {:L [{:M {:K {:L [{:S "L"}]}}}]}
          :___ {:SS ["X"]}}
   :last-evaluated-table-name :TABLE_n_a_m_e
   :table-names [:t_able_name1
                 :ta_bl_ename2]
   :count 51
   :items [{:COL_NAME {:S "COL_NAME"}
            :OTHER_CN {:L [{:L [{:S " "}]}]}}
           {:COL_NAME {:S "COL_NAME"}}]
   :scanned-count 72
   :last-evaluated-key {:SSSS {:S "SSSS"}}})
