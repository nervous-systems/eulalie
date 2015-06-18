(ns eulalie.dynamo-streams.key-types
  (:require [eulalie.dynamo.key-types :as dynamo.key-types]))

(def request-key-types
  (assoc dynamo.key-types/request-key-types
         :shard-iterator-type :enum))

(def response-key-types
  (assoc dynamo.key-types/response-key-types
         :stream-description :nest
         :stream-status :enum
         :stream-view-type :enum
         :sequence-number-range :nest
         :shards :nest
         :records :nest
         :event-name :enum
         :aws-region :keys
         :dynamodb :nest))
