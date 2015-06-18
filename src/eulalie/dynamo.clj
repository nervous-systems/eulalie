(ns eulalie.dynamo
  (:require [cemerick.url :refer [url]]
            [eulalie.sign :as sign]
            [eulalie.service-util :refer :all]
            [eulalie.dynamo.mapping :as mapping]
            [eulalie.util :refer :all]
            [eulalie :refer :all]
            [cheshire.core :as cheshire]
            [clojure.core.async :as async]
            [camel-snake-kebab.core :refer [->PascalCaseString ->kebab-case-keyword]]))

;; FIXME how do we handle errors in the body like {__type:} in a
;; general way?

(defn body->error [{:keys [__type message Message]}]
  (when-let [t (some-> __type
                       not-empty
                       (from-last-match "#")
                       ->kebab-case-keyword)]
    {:type t :message (or Message message)}))

(defn req-target [prefix {:keys [target]}]
  (str prefix (->camel-s target)))

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
   :max-retries 10})

(defmethod prepare-request :eulalie.service/dynamo [req]
  (let [req (default-request service-defaults req)]
    (-> req
        (update-in [:headers] merge
                   {:content-type "application/x-amz-json-1.0"
                    :x-amz-target (req-target target-prefix req)})
        (assoc :service-name service-name))))

(defmethod transform-request-body :eulalie.service/dynamo [{:keys [body] :as req}]
  (assoc req :body (some-> body mapping/transform-request cheshire/encode)))

(defmethod transform-response-body :eulalie.service/dynamo [req body]
  (some-> body (cheshire/decode true) mapping/transform-response))

(defmethod transform-response-error :eulalie.service/dynamo [req resp]
  (some-> resp :body (cheshire/decode true) body->error))

(defmethod request-backoff :eulalie.service/dynamo [req retries error]
  (backoff retries))
