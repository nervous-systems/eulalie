(ns eulalie.sns
  (:require [eulalie :refer :all]
            [cemerick.url :as url]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as csk-extras]
            [eulalie.service-util :as service-util]
            [eulalie.util :refer :all]
            [eulalie.sign :as sign]
            [eulalie.util.xml :as x]
            [eulalie.util.query :as q]
            [cheshire.core :as json]))

(let [kv-spec [:kv "Attributes.entry" "key" "value" #{:enum}]]
  (def target->seq-spec
    {:add-permission
     {:accounts [:list "AWSAccountId.member"]
      :actions  [:list "ActionName.member" #{:enum}]}
     :create-platform-application
     {:attrs kv-spec}
     :create-platform-endpoint
     {:attrs kv-spec}
     :set-platform-application-attributes
     {:attrs kv-spec}
     :set-endpoint-attributes
     {:attrs kv-spec}}))

(def enum-keys-out #{:attribute-name})

(defmulti  prepare-body (fn [target body] target))
(defmethod prepare-body :default [_ body] body)

(defmulti  prepare-message-value (fn [t value] t))
(defmethod prepare-message-value :default [_ v] v)
(defmethod prepare-message-value :GCM [_ v]
  (csk-extras/transform-keys csk/->snake_case_keyword v))

(let [dispatch-map {:APNS_SANDBOX :APNS}
      upper-case   #{:APNS_SANDBOX :APNS :GCM}]
  (defn prepare-targeted-message [msg]
    (into {}
      (for [[k v] msg]
        (let [k' (csk/->SNAKE_CASE_KEYWORD k)
              k  (if (upper-case k') k' k)
              v  (prepare-message-value
                  (dispatch-map k k)
                  v)]
          [k (cond-> v (map? v) json/encode)])))))

(defmethod prepare-body :publish [_ {:keys [message] :as body}]
  (cond-> body
    (map? message)
    (assoc :message-structure :json
           :message (-> message
                        prepare-targeted-message
                        json/encode))))


(defrecord SNSService [endpoint version max-retries]
  AmazonWebService

  (prepare-request [service {:keys [target] :as req}]
    (let [{:keys [body] :as req} (q/prepare-query-request service req)]
      (assoc req :body
             (as-> body %
               (q/expand-sequences  % (target->seq-spec target))
               (q/translate-enums   % enum-keys-out)
               (prepare-body target %)))))

  (transform-request [_ body]
    (-> body q/format-query-request url/map->query))

  (transform-response [_ resp]
    ;; Yeah, strings
    ;; So, we're not translating the enums on the way back out, because we're
    ;; not doing _any_ response translation.  We should fold some stuff from
    ;; fink-nottle back in
    (x/string->xml-map resp))

  (transform-response-error [_ {:keys [body] :as resp}]
    (x/parse-xml-error body))

  (request-backoff [_ retry-count error]
    (service-util/default-retry-backoff retry-count error))

  (sign-request [_ req]
    (sign/aws4-sign "sns" req)))

(def service
  (SNSService.
   (url/url "https://sns.us-east-1.amazonaws.com")
   "2010-03-31"
   3))
