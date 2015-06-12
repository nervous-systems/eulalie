(ns eulalie.sns
  (:require [eulalie :refer :all]
            [cemerick.url :as url]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as csk-extras]
            [eulalie.service-util :as service-util]
            [eulalie.util :as util]
            [eulalie.sign :as sign]
            [eulalie.util.xml :as x]
            [eulalie.util.query :as q]
            [cheshire.core :as json]))

(let [kv-spec [:kv [:attributes :entry] :key :value]]
  (def target->seq-spec
    {:add-permission
     {:accounts [:list ["AWSAccountId" :member]]
      :actions  [:list [:action-name :member]]}
     :create-platform-application
     {:attrs kv-spec}
     :create-platform-endpoint
     {:attrs kv-spec}
     :set-platform-application-attributes
     {:attrs kv-spec}
     :set-endpoint-attributes
     {:attrs kv-spec}}))

(def enum-keys-out
  #{:attribute-name
    :action-name
    #(and (= (first %) :attributes) (= (last %) :key))})

(defmulti  prepare-body (fn [target body] target))
(defmethod prepare-body :default [_ body] body)

(defn prepare-attr-req [{:keys [name value] :as body}]
  (assoc body
         :attribute-name name
         :attribute-value (cond-> value
                            (= name :delivery-policy) q/nested-json-out
                            (= name :policy) q/nested-json-out)))

(defmethod prepare-body :set-subscription-attributes [_ body]
  (prepare-attr-req body))
(defmethod prepare-body :set-topic-attributes [_ body]
  (prepare-attr-req body))

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

(defmulti  restructure-response (fn [target elem] target))
(defmethod restructure-response :default [_ e] e)

(defn application-arn [m] (x/child-content m :platform-application-arn))
(defn endpoint-arn    [m] (x/child-content m :endpoint-arn))

(defn restructure-members [m apply-me]
  (with-meta
    (map apply-me (x/children m :member))
    {:next-token (x/child-content m :next-token)}))

(defmethod restructure-response :list-platform-applications [_ m]
  (restructure-members m #(assoc (x/attrs->map %) :arn (application-arn %))))
(defmethod restructure-response :list-endpoints-by-platform-application [_ m]
  (restructure-members m #(assoc (x/attrs->map %) :arn (endpoint-arn %))))

(defmethod restructure-response :subscribe [_ result]
  (if (= result "pending confirmation")
    :eulalie.sns/pending
    result))

(defn fix-subscription-arn [x]
  ;; yeah, different
  (if (= x "PendingConfirmation") :eulalie.sns/pending x))

(defmethod restructure-response :confirm-subscription [_ arn]
  (fix-subscription-arn arn))

(def subscription-attrs
  #{:protocol :owner :topic-arn :subscription-arn :endpoint})

(defn restructure-subscription [m]
  (-> m
      (x/child-content->map subscription-attrs)
      (update-in [:subscription-arn] fix-subscription-arn)))

(defn restructure-subscriptions [resp]
  (map restructure-subscription (x/children resp :member)))

(defmethod restructure-response :list-subscriptions [_ m]
  (restructure-members m restructure-subscription))
(defmethod restructure-response :list-subscriptions-by-topic [_ m]
  (restructure-members m restructure-subscription))

(defn handle-json-keys-in
  [{:keys [policy delivery-policy effective-delivery-policy] :as m}]
  (cond-> m
    policy (assoc :policy (q/nested-json-in policy))
    delivery-policy (assoc :delivery-policy (q/nested-json-in delivery-policy))
    effective-delivery-policy
    (assoc :effective-delivery-policy
           (q/nested-json-in effective-delivery-policy))))

(defmethod restructure-response :get-topic-attributes [_ m]
  (handle-json-keys-in m))
(defmethod restructure-response :get-subscription-attributes [_ m]
  (handle-json-keys-in m))

(def target->elem-spec
  {:create-platform-endpoint [:one :endpoint-arn]
   :create-topic [:one :topic-arn]
   :publish [:one :message-id]
   :subscribe [:one :subscription-arn]
   :confirm-subscription [:one :subscription-arn]
   :create-platform-application [:one :platform-application-arn]
   :get-topic-attributes [:attrs]
   :get-subscription-attributes [:attrs]
   :get-platform-application-attributes [:attrs]
   :get-endpoint-attributes [:attrs]})

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
    (-> body q/format-query-request q/log-query url/map->query))

  (transform-response [_ resp]
    ;; Yeah, strings
    ;; So, we're not translating the enums on the way back out, because we're
    ;; not doing _any_ response translation.  We should fold some stuff from
    ;; fink-nottle back in
    (let [elem   (x/string->xml-map resp)
          [tag]  (keys elem)
          target (keyword (util/to-first-match (name tag) "-response"))]
      (->> (x/extract-response-value target elem target->elem-spec)
           (restructure-response target))))

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
