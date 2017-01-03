(ns eulalie.service.dynamo
  (:require [eulalie.service :as service]
            [eulalie.service.generic.json :as generic.json]
            [eulalie.service.dynamo.request]
            [eulalie.service.dynamo.impl.key-types :as key-types]
            [promesa.core :as p]
            [eulalie.service.impl.json.mapping :as json.mapping]))

(derive :eulalie.service/dynamo :eulalie.service.generic.json/request)
(derive :eulalie.service/dynamo :eulalie.service.generic.json/response)

(defmethod service/defaults :eulalie.service/dynamo [_]
  {:region                             "us-east-1"
   :eulalie.sign/service               "dynamodb"
   :eulalie.service.json/target-prefix "DynamoDB_20120810."
   :max-retries                        10})

(let [scale-factor   25
      max-backoff-ms (* 20 1000)
      max-retries    9]
  (defn backoff [retries]
    (cond (< retries 0)           (p/resolved nil)
          (< max-retries retries) (p/delay max-backoff-ms)
          :else (-> 1
                    (bit-shift-left retries)
                    (* scale-factor)
                    (min max-backoff-ms)
                    p/delay))))

(defmethod service/request-backoff :eulalie.service/dynamo [req retries error]
  (backoff retries))

(defmethod generic.json/map-request-keys :eulalie.service/dynamo
  [{:keys [body target]}]
  (json.mapping/transform-request (or body {}) key-types/request-key-types))

(defmethod generic.json/map-response-keys :eulalie.service/dynamo [{:keys [body]}]
  (json.mapping/transform-response body key-types/response-key-types))
