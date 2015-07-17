(ns eulalie.dynamo
  (:require [eulalie.core :as eulalie]
            [eulalie.util.json.mapping :as json.mapping]
            [eulalie.dynamo.key-types :as key-types]
            [eulalie.util.json :as util.json]
            #? (:clj
                [clojure.core.async :as async]
                :cljs
                [cljs.core.async :as async])))

(derive :eulalie.service/dynamo :eulalie.service.generic/json-response)
(derive :eulalie.service/dynamo :eulalie.service.generic/json-request)

(def service-name "dynamodb")
(def target-prefix "DynamoDB_20120810.")

(def service-defaults
  {:region "us-east-1"
   :service-name service-name
   :target-prefix target-prefix
   :max-retries 10})

(defmethod eulalie/prepare-request :eulalie.service/dynamo [req]
  (util.json/prepare-json-request service-defaults req))

(defmethod util.json/map-request-keys :eulalie.service/dynamo [{:keys [body]}]
  (json.mapping/transform-request body key-types/request-key-types))

(defmethod util.json/map-response-keys :eulalie.service/dynamo [{:keys [body]}]
  (json.mapping/transform-response body key-types/response-key-types))

(let [scale-factor   25
      max-backoff-ms (* 20 1000)
      max-retries    9]
  (defn backoff [retries]
    (cond (< retries 0) nil
          (< max-retries retries) (async/timeout max-backoff-ms)
          :else (-> 1
                    (bit-shift-left retries)
                    (* scale-factor)
                    (min max-backoff-ms)
                    async/timeout))))

(defmethod eulalie/request-backoff :eulalie.service/dynamo [req retries error]
  (backoff retries))
