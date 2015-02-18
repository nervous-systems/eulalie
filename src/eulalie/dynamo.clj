(ns eulalie.dynamo
  (:require [cemerick.url :refer [url]]
            [eulalie.sign :as sign]
            [eulalie.service-util :refer :all]
            [eulalie.dynamo.mapping :as mapping]
            [eulalie.util :refer :all]
            [eulalie :refer :all]
            [cheshire.core :as cheshire]
            [camel-snake-kebab.core :refer [->CamelCaseString ->kebab-case-keyword]]))


;; FIXME how do we handle errors in the body like {__type:} in a
;; general way?

(def body->error-type ;; something like this, have service handle it
  (fn-some->
   :__type not-empty (from-last-match "#") not-empty
   ->kebab-case-keyword))

;; (def service
;;   {:request-defaults
;;    {:method :post
;;     :endpoint (url "https://dynamodb.us-east-1.amazonaws.com/")
;;     :max-retries 10
;;     :headers {:content-type "applicaton/x-amz-json-1.0"}}
;;    :compute-headers
;;    (fn [{:keys [target]}]
;;      {:content-type "application/x-amz-json-1.0"
;;       :x-amz-target  (str "DynamoDB_20120810." (->CamelCaseString target))})
;;    :aws-name "dynamodb"
;;    :transform-request  (fn-some-> mapping/transform-request cheshire/encode)
;;    :transform-response (fn-some-> (cheshire/decode true) mapping/transform-response)
;;    :body->error        (fn-some-> :body (cheshire/decode true) body->error-type)
;;    :backoff-strategy   default-retry-backoff ;; or not
;;    :signer             sign/aws4-sign})

(defn req-target [prefix {:keys [target]}]
  (str prefix (->camel-s target)))

(defrecord DynamoService [endpoint target-prefix max-retries]
  AmazonWebService

  (prepare-request [{:keys [endpoint target-prefix content-type]} req]
    (let [headers
          {:content-type "application/x-amz-json-1.0"
           :x-amz-target (req-target target-prefix req)}
          req (merge {:max-retries max-retries
                      :method :post
                      :endpoint endpoint} req)]
      (update-in req [:headers] merge headers)))

  (transform-request [_ req]
    (some-> req mapping/transform-request cheshire/encode))

  (transform-response [_ resp]
    (some-> resp (cheshire/decode true) mapping/transform-response))

  (transform-response-error [_ resp]
    (some-> resp :body (cheshire/decode true) body->error-type))

  (request-backoff [_ retry-count error]
    (default-retry-backoff retry-count error)) ;;wrong

  (sign-request [_ creds req]
    (sign/aws4-sign "dynamodb" creds req)))

(def service
  (DynamoService.
   (url "https://dynamodb.us-east-1.amazonaws.com/")
   "DynamoDB_20120810."
   10))
