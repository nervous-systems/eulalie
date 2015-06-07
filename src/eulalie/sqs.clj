(ns eulalie.sqs
  (:require [eulalie]
            [eulalie.util.xml   :as x]
            [eulalie.util.query :as q]
            [eulalie.service-util :as service-util]
            [eulalie.sign :as sign]

            [cemerick.url :as url]))

(def target->seq-spec
  {:add-permission
   {:accounts [:list "AWSAccountId"]
    :actions  [:list "ActionName" #{:camel}]}
   :change-message-visibility-batch
   {:entries [:list "ChangeMessageVisibilityBatchRequestEntry"]}
   :create-queue
   {:attrs   [:kv   "Attribute" "Name" "Value" #{:camel}]}
   :delete-message-batch
   {:entries [:map  "DeleteMessageBatchRequestEntry"]}
   :get-queue-attributes
   {:attrs   [:list "AttributeName" #{:camel}]}})

(defmulti  prepare-body (fn [target req] target))
(defmethod prepare-body :default [_ req] req)

(defmethod prepare-body :receive-message [_ {:keys [meta attrs] :as body}]
                                        ;; ...
  )

(defrecord SQSService [endpoint version max-retries]
  eulalie/AmazonWebService

  (prepare-request [service {:keys [target] :as req}]
    (-> service
        (q/prepare-query-request req)
        (update-in [:body] (partial prepare-body target))))

  (transform-request [_ body]
    (-> body q/format-query-request url/map->query))

  (transform-response [_ resp]
    (x/string->xml-map resp))

  (transform-response-error [_ {:keys [body] :as resp}]
    (x/parse-xml-error body))

  (request-backoff [_ retry-count error]
    (service-util/default-retry-backoff retry-count error))

  (sign-request [_ req]
    (sign/aws4-sign "sns" req)))

(def service
  (SQSService.
   (url/url "https://sqs.us-east-1.amazonaws.com")
   "2012-11-05"
   3))
