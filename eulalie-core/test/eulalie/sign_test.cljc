(ns eulalie.sign-test
  (:require [eulalie.sign :as sign]
            [cemerick.url :refer [url]]
            [clojure.string :as str]
            #?(:clj  [clojure.test :as t]
               :cljs [cljs.test :as t :include-macros true])))

(defn decompose-auth [s]
  (let [[h & t] (str/split s #",*\s+")]
    [h (into {} (map #(str/split % #"=" 2) t))]))

(def req
  {:body     "{\"TableName\":\"table-name\"}"
   :creds    {:access-key "rofl" :secret-key "lol a secret key"}
   :endpoint (url "http://dynamodb.us-east-1.amazonaws.com:80")
   :date     1436795614947
   :headers  {:content-type   "application/x-amz-json-1.0"
              :x-amz-target   "DynamoDB_20120810.DescribeTable"
              :content-length 26}
   :method   :post
   :region   :us-east-1
   :eulalie.sign/service "dynamodb"})

(def expected-auth
  ["AWS4-HMAC-SHA256"
   {"Credential"    "rofl/20150713/us-east-1/dynamodb/aws4_request"
    "SignedHeaders" "content-length;content-type;host;x-amz-date;x-amz-target"
    "Signature"     "c8f0aee6b2ab806881b5afd93964c774c9bb60d313c08e4b803e5cc021001bac"}])

(t/deftest aws4 []
  (let [auth (-> (sign/aws4 req)
                 :headers
                 :authorization
                 decompose-auth)]
    (t/is (= expected-auth auth))))
