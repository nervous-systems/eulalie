(ns eulalie.sns
  (:require [eulalie :refer :all]
            [cemerick.url :refer [url map->query]]
            [clojure.tools.logging :as log]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [clojure.xml :as xml]
            [eulalie.service-util :refer :all]
            [clojure.walk :as walk]
            [eulalie.util :refer :all]
            [eulalie.sign :as sign]
            [cheshire.core :as json]))

(defn format-attr-entries
  ([prefix attrs]
   (into {}
     (map-indexed
      (fn [i [k v]]
        (let [prefix (str prefix "." (inc i) ".")]
          {(str prefix "key") (->camel-s k)
           (str prefix "value") v}))
      attrs)))
  ([attrs]
   (merge
    (format-attr-entries "attributes.entry" attrs))))

(defn format-kvs [m]
  (walk/postwalk
   (fn [x]
     (if (map? x)
       (into {} (for [[k v] x]
                  [(->camel-s k)
                   (cond-> v (keyword? v) name)]))
       x))
   m))

(defn xml-map [mess]
  ;; I don't really know what people do
  (walk/postwalk
   (fn [x]
     (if (and (map? x) (:tag x))
       (let [{:keys [tag content]} x]
         {(->kebab-k tag) content})
       x))
   mess))

(defn node-seq [x-map]
  (tree-seq map? #(apply concat (vals %)) x-map))

(defn child [container x]
  (some x (node-seq container)))

(def child-content (comp first child))

(defn string->xml-map [^String resp]
  (some->
   resp
   (.getBytes "UTF-8")
   java.io.ByteArrayInputStream.
   xml/parse
   xml-map))

(defmulti  prepare-body (fn [target body] target))
(defmethod prepare-body :default [_ body] body)
(defmethod prepare-body :create-platform-application [_ {:keys [attrs] :as body}]
  (into body (format-attr-entries (:attrs body))))

(defmulti  prepare-message-value (fn [t value] t))
(defmethod prepare-message-value :default [_ v] v)
(defmethod prepare-message-value :GCM [_ v]
  (transform-keys csk/->snake_case_keyword v))

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

(defn default-request [{:keys [max-retries endpoint]} req]
  (merge {:max-retries max-retries
          :endpoint endpoint
          :method :post} req))

(defrecord SNSService [endpoint version max-retries]
  AmazonWebService

  (prepare-request [{:keys [version] :as service}
                    {:keys [target body headers] :as req}]
    (let [body (prepare-body
                target
                (assoc body
                       :version version
                       :action (->camel-s target)))]
      (-> (default-request service req)
          (assoc :body body)
          (assoc-in [:headers :content-type]
                    "application/x-www-form-urlencoded"))))

  (transform-request [_ body]
    (-> body format-kvs map->query))

  (transform-response [_ resp]
    ;; Yeah, strings
    (string->xml-map resp))

  (transform-response-error [_ {:keys [body] :as resp}]
    (if-let [m (string->xml-map body)]
      {:type (->kebab-k (child-content m :code))
       :message (child-content m :message)}))

  (request-backoff [_ retry-count error]
    (default-retry-backoff retry-count error))

  (sign-request [_ req]
    (sign/aws4-sign "sns" req)))

(def service
  (SNSService.
   (url "https://sns.us-east-1.amazonaws.com")
   "2010-03-31"
   3))
