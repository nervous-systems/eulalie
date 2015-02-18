(ns eulalie.sns
  (:require [cemerick.url :refer [url map->query]]
            [camel-snake-kebab.core :refer [->CamelCaseKeyword ->kebab-case-keyword]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [clojure.xml :as xml]
            [eulalie.service-util :refer :all]
            [clojure.walk :as walk]
            [eulalie.util :refer :all]
            [eulalie.sign :as sign]
            [eulalie :refer :all]))

(defrecord SNSService [endpoint version max-retries]
  AmazonWebService

  (prepare-request [{:keys [endpoint version max-retries]}
                    {:keys [target content] :as req}]
    (let [req (merge {:max-retries max-retries :endpoint endpoint :method :post} req)
          req (rewrite-map
               req
               {:content (fn->> (conj {:action (->camel-s target) :version version})
                                ->camel-keys-s map->query)
                :headers #(assoc % :content-type "application/x-www-form-urlencoded")})]
      (clojure.pprint/pprint req)
      req))

  (transform-request [_ req]
    req)

  (transform-response [_ resp]
    ;; Yeah, strings
    (some-> ^String resp (.getBytes "UTF-8") java.io.ByteArrayInputStream. xml/parse))

  (transform-response-error [_ resp]
    nil)

  (request-backoff [_ retry-count error]
    (default-retry-backoff retry-count error))

  (sign-request [_ creds req]
    (sign/aws4-sign "sns" creds req)))

(def service
  (SNSService.
   (url "https://sns.us-east-1.amazonaws.com")
   "2010-03-31"
   3))
