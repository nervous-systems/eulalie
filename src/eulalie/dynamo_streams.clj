(ns eulalie.dynamo-streams
  (:require [eulalie]
            [eulalie.util.json.mapping :as json.mapping]
            [eulalie.util.json :as util.json]
            [eulalie.dynamo-streams.key-types :as key-types]
            [eulalie.service-util :as service-util]))

(derive :eulalie.service/dynamo-streams :eulalie.service/dynamo)

(def service-name "dynamodbstreams")
(def target-prefix "DynamoDBStreams_20120810.")

(def service-defaults
  {:region "us-east-1"
   :service-name service-name
   :target-prefix target-prefix
   :max-retries 3})

(defmethod util.json/map-request-keys
  :eulalie.service/dynamo-streams
  [{:keys [body]}]
  (json.mapping/transform-request body key-types/request-key-types))

(defmethod util.json/map-response-keys
  :eulalie.service/dynamo-streams
  [{:keys [body]}]
  (json.mapping/transform-response body key-types/response-key-types))

;; We don't want the dynamo backoff strategy
(defmethod eulalie/request-backoff :eulalie.service/dynamo-streams
  [req retries error]
  (service-util/default-retry-backoff retries error))
