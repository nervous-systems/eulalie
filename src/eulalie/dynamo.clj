(ns eulalie.dynamo
  (:require [cemerick.url :refer [url]]
            [eulalie.sign :as sign]
            [eulalie.service-util :refer :all]
            [eulalie.dynamo.mapping :as mapping]
            [eulalie.util :refer :all]
            [eulalie :refer :all]
            [cheshire.core :as cheshire]
            [clojure.core.async :as async]
            [camel-snake-kebab.core :refer [->CamelCaseString ->kebab-case-keyword]]))

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

(defrecord DynamoService [endpoint target-prefix max-retries]
  AmazonWebService

  (prepare-request [{:keys [endpoint target-prefix content-type]} req]
    (let [req (merge {:max-retries max-retries
                      :method :post
                      :endpoint endpoint} req)]
      (update-in req [:headers] merge
                 {:content-type "application/x-amz-json-1.0"
                  :x-amz-target (req-target target-prefix req)})))

  (transform-request [_ req]
    (some-> req mapping/transform-request cheshire/encode))

  (transform-response [_ resp]
    (some-> resp (cheshire/decode true) mapping/transform-response))

  (transform-response-error [_ resp]
    (some-> resp :body (cheshire/decode true) body->error))

  (request-backoff [_ retry-count error]
    (backoff retry-count))

  (sign-request [_ req]
    (sign/aws4-sign "dynamodb" req)))

(def service
  (DynamoService.
   (url "https://dynamodb.us-east-1.amazonaws.com/")
   "DynamoDB_20120810."
   10))
