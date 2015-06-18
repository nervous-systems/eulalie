(ns eulalie.dynamo
  (:require [cemerick.url :refer [url]]
            [eulalie.sign :as sign]
            [eulalie.service-util :refer :all]
            [eulalie.dynamo.mapping :as mapping]
            [eulalie.util :refer :all]
            [eulalie.util.json :as util.json]
            [eulalie :refer :all]
            [cheshire.core :as cheshire]
            [clojure.core.async :as async]
            [camel-snake-kebab.core :refer [->PascalCaseString ->kebab-case-keyword]]))

(derive :eulalie.service/dynamo :eulalie.service.generic/json-response)

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

(def service-name "dynamodb")
(def target-prefix "DynamoDB_20120810.")

(def service-defaults
  {:region "us-east-1"
   :service-name service-name
   :target-prefix target-prefix
   :max-retries 10})

(defmethod prepare-request :eulalie.service/dynamo [req]
  (util.json/prepare-json-request service-defaults req))

(defmethod transform-request-body :eulalie.service/dynamo [{:keys [body] :as req}]
  (assoc req :body (some-> body mapping/transform-request cheshire/encode)))

(defmethod transform-response-body :eulalie.service/dynamo [req body]
  (some-> body (cheshire/decode true) mapping/transform-response))

(defmethod request-backoff :eulalie.service/dynamo [req retries error]
  (backoff retries))
