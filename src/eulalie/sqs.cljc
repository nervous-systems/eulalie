(ns eulalie.sqs
  (:require [camel-snake-kebab.core :as csk]
            [cemerick.url :as url]
            [clojure.set :as set]
            [eulalie.core :as eulalie]
            [eulalie.util.service :as util.service]
            [eulalie.util.query :as q]
            [eulalie.util.xml   :as x]
            [eulalie.platform.xml :as platform.xml]
            [plumbing.core :refer [map-vals]]))

(derive :eulalie.service/sqs :eulalie.service.generic/xml-response)
(derive :eulalie.service/sqs :eulalie.service.generic/query-request)

(def target->seq-spec
  {:add-permission
   {:accounts [:list "AWSAccountId"]
    :actions  [:list :action-name]}
   :change-message-visibility-batch
   {:messages [:maps :change-message-visibility-batch-request-entry]}
   :create-queue
   {:attrs   [:kv :attribute :name :value]}
   :delete-message-batch
   {:messages [:maps :delete-message-batch-request-entry]}
   :get-queue-attributes
   {:attrs   [:list :attribute-name]}
   :receive-message
   {:meta   [:list :attribute-name]
    :attrs  [:list :message-attribute-name]}})

(def enum-keys-out
  #{:attribute-name
    #(and (= (first %) :attribute) (= (last %) :name))})

(defmulti  prepare-body (fn [target req] target))
(defmethod prepare-body :default [_ req] req)

(defmethod prepare-body :get-queue-url [_ body]
  ;; The case deviates from the other keys
  (set/rename-keys body {:queue-owner-aws-account-id
                         "QueueOwnerAWSAccountId"}))

(defmethod prepare-body :add-permission [_ {:keys [actions] :as m}]
  (assoc m :actions (map q/policy-key-out actions)))

(defmethod prepare-body :receive-message [_ body]
  (set/rename-keys body {:maximum :maximum-number-of-messages
                         :wait-seconds :wait-time-seconds}))

(defmethod prepare-body :create-queue
  [_ {{:keys [policy redrive-policy] :as attrs} :attrs :as body}]
  (assoc body
         :attrs
         (cond-> attrs
           policy (assoc :policy (q/policy-json-out policy))
           redrive-policy (assoc :redrive-policy
                                 (q/nested-json-out redrive-policy)))))

(defn prepare-message [{:keys [attrs] :as message}]
  (-> message
      (conj (q/message-attrs->dotted attrs))
      (set/rename-keys {:body :message-body})))

(defmethod prepare-body :send-message [_ body]
  (prepare-message body))
(defmethod prepare-body :send-message-batch [_ {:keys [messages] :as body}]
  (conj body
        (q/map-list->dotted
         :send-message-batch-request-entry
         (map prepare-message messages))))

(defmethod prepare-body :set-queue-attributes [_ {:keys [name value] :as body}]
  (assoc body
         [:attribute :name] name
         [:attribute :value] (cond-> value
                               (= name :policy) q/policy-json-out
                               (= name :redrive-policy) q/nested-json-out)))

(defmulti  restructure-response (fn [target body] target))
(defmethod restructure-response :default [_ body] body)

(defn attributes->map [body & [{:keys [parent] :or {parent :attribute}}]]
  (into {}
    (for [attr (x/children body parent)]
      [(-> attr
           (x/child-content :name)
           csk/->kebab-case-keyword)
       (x/child-content attr :value)])))

(defmethod restructure-response :get-queue-attributes [_ body]
  (let [{:keys [policy redrive-policy] :as attrs} (attributes->map body)]
    (cond-> attrs
      policy (assoc :policy (q/policy-json-in policy))
      redrive-policy (assoc :redrive-policy (q/nested-json-in redrive-policy)))))

(defmethod restructure-response :send-message [_ body]
  (x/child-content->map body {:message-id :id :md-5-of-message-body :body-md5}))

(defn attr-type->value-key [type]
  (case type
    :string :string-value
    :number :string-value
    :binary :binary-value))

(defn message-attr->kv [attr]
  (let [a-name (x/child-content attr :name)
        value-container (x/child attr :value)
        type (keyword (x/child-content value-container :data-type))
        in-type ({:Binary :binary
                  :String :string
                  :Number :number} type type)
        value-key (attr-type->value-key in-type)
        value (x/child-content value-container value-key)]
    [(keyword a-name) [in-type value]]))

(defn message-attrs->map [message]
  (into {}
    (for [attr (x/children message :message-attribute)]
      (message-attr->kv attr))))

(defn restructure-message [message]
  (-> message
      (conj (x/child-content->map
             message
             {:body :body :md-5-of-body :body-md5 :message-id :id
              :receipt-handle :receipt-handle}))
      (assoc :attrs (message-attrs->map message)
             :meta  (attributes->map message))
      (dissoc :message)))

(defmethod restructure-response :receive-message [_ body]
  (for [message (x/children body :message)]
    (restructure-message message)))

(defn children->id-set [node children-of]
  (->> (x/children node children-of)
       (map #(x/child-content % :id))
       (into #{})))

(defn children-by-attr [parent unique-attr attrs]
  (->> (map #(x/child-content->map % attrs) parent)
       (group-by unique-attr)
       (map-vals first)))

(def failed-batch-attr-renames
  {:code :code :id :batch-id :message :message :sender-fault :sender-fault})

(defn restructure-failed-batch [body]
  (let [ms (-> body
               (x/children :batch-result-error-entry)
               (children-by-attr :batch-id failed-batch-attr-renames))]
    (map-vals #(update-in % [:code] csk/->kebab-case-keyword) ms)))

(defmethod restructure-response :delete-message-batch [_ body]
  {:succeeded
   (children->id-set body :delete-message-batch-result-entry)
   :failed
   (restructure-failed-batch body)})

(defn restructure-successful-sends [body]
  (children-by-attr
   (x/children body :send-message-batch-result-entry)
   :batch-id
   {:id :batch-id :message-id :id :md-5-of-message-attributes :attr-md5
    :md-5-of-message-body :body-md5}))

(defmethod restructure-response :send-message-batch [_ body]
  {:succeeded
   (restructure-successful-sends body)
   :failed
   (restructure-failed-batch body)})

(defmethod restructure-response :change-message-visibility-batch [_ body]
  {:succeeded
   (children->id-set body :change-message-visibility-batch-result-entry)
   :failed
   (restructure-failed-batch body)})

(def target->elem-spec
  {:create-queue  [:one :queue-url]
   :get-queue-url [:one :queue-url]
   :list-queues   [:many :queue-url]
   :list-dead-letter-source-queues [:many :queue-url]})

(def service-name "sqs")

(def service-defaults
  {:version "2012-11-05"
   :region "us-east-1"
   :service-name service-name
   :max-retries 3})

(defmethod eulalie/prepare-request :eulalie.service/sqs [{:keys [target] :as req}]
  (let [{:keys [body] :as req} (q/prepare-query-request service-defaults req)]
    (assoc req
           :service-name service-name
           :body (as-> body %
                   (prepare-body target %)
                   (q/expand-sequences  % (target->seq-spec target))
                   (q/translate-enums   % enum-keys-out)))))

(defmethod eulalie/transform-response-body :eulalie.service/sqs
  [{{:keys [target]} :request body :body}]
  (let [elem (platform.xml/string->xml-map body)]
    (->> (x/extract-response-value target elem target->elem-spec)
         (restructure-response target))))
