(ns eulalie.dynamo-streams
  (:require [eulalie.core :as eulalie]
            [eulalie.dynamo]
            [eulalie.util.json.mapping :as json.mapping]
            [eulalie.util.json :as util.json]
            [eulalie.dynamo-streams.key-types :as key-types]
            [eulalie.util.service :as util.service]))

(derive :eulalie.service/dynamo-streams :eulalie.service/dynamo)

(def service-name "dynamodb")
(def target-prefix "DynamoDBStreams_20120810.")

(def service-defaults
  {:region "us-east-1"
   :service-name service-name
   :target-prefix target-prefix
   :max-retries 3})

(defn req->endpoint [req]
  (:endpoint
   (util.service/default-request
     (assoc service-defaults
            :service-name "streams.dynamodb")
     req)))

(defmethod eulalie/prepare-request :eulalie.service/dynamo-streams
  [{:keys [endpoint] :as req}]
  (let [req' (util.json/prepare-json-request service-defaults req)]
    ;; If the user didn't specify the endpoint, recalculating using a
    ;; different service name, because the URL scheme appears to be different
    ;; for Streams
    (cond-> req'
      (not endpoint)
      (assoc :endpoint (req->endpoint req)))))

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
  (util.service/default-retry-backoff retries error))
