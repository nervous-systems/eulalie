(ns eulalie.sign-test
  (:require [eulalie.sign :refer :all]
            [clojure.test :refer :all]
            [cemerick.url :refer [url]]
            [eulalie.util :refer :all]
            [clojure.string :as string]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]])
  (:import eulalie.TestableAWS4Signer
           java.util.Date
           [com.amazonaws.auth
            SignerFactory
            Signer
            RegionAwareSigner
            AWSCredentials
            BasicAWSCredentials
            AWS4Signer]
           [com.amazonaws
            AmazonWebServiceRequest
            AmazonServiceException
            AmazonClientException
            AmazonServiceException$ErrorType
            Request
            SDKGlobalConfiguration]
           [com.amazonaws.http HttpResponse JsonErrorResponseHandler JsonResponseHandler]
           [com.amazonaws.transform Marshaller Unmarshaller]
           [com.amazonaws.util AwsHostNameUtils]
           [java.io IOException]
           [com.amazonaws.services.dynamodbv2.model
            DescribeTableRequest]
           [com.amazonaws.services.dynamodbv2.model.transform
            DescribeTableRequestMarshaller]))

(def creds {:secret-key "lol a secret key"
            :access-key "rofl"})

(def overrides {:service-name "webservice"})
(def endpoint (url "https://dynamodb.us-east-1.amazonaws.com"))

(defn ^TestableAWS4Signer aws4-signer* [date]
  (doto (TestableAWS4Signer. true)
    (.setOverriddenDate date)))

;; "AWS4-HMAC-SHA256
;; Credential=rofl/20141204/us-east-1/dynamodb/aws4_request,
;; SignedHeaders=content-length;content-type;host;x-amz-date;x-amz-target,
;; Signature=23a5f9122025b561e665408c0b64b18bc91c4262bdd647c6d97b45ad4d058418"

(defn decompose-auth [s]
  (let [[h & t] (string/split s #",*\s+")]
    [h (into {} (map #(string/split % #"=" 2) t))]))

(deftest aws4
  (let [date    (Date.)
        signer  (aws4-signer* date)

        ^Request aws-req
        (doto (.marshall
               (DescribeTableRequestMarshaller.)
               (DescribeTableRequest. "table-name"))
          (.setEndpoint (java.net.URI. (str endpoint))))]

    (.sign signer aws-req (BasicAWSCredentials.
                           (:access-key creds)
                           (:secret-key creds)))

    (let [{auth "Authorization" :as headers} (into {} (.getHeaders aws-req))
          headers (dissoc headers "Authorization")
          req {:content (-> aws-req .getContent slurp)
               :date (.getTime date)
               :endpoint endpoint
               :method :post
               :headers headers
               :creds creds}
          auth' (-> (aws4-sign "dynamodb" req)
                    :headers
                    :authorization)]
      (is (= (decompose-auth auth) (decompose-auth auth'))))))
