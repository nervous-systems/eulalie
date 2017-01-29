(ns eulalie.sign-test
  (:require [#?(:clj clojure.spec.test :cljs cljs.spec.test) :as stest]
            [eulalie.sign :as sign]
            [cemerick.url :refer [url]]
            [clojure.string :as str]
            #?(:clj  [clojure.test :as t]
               :cljs [cljs.test :as t :include-macros true])))

(stest/instrument)

(defn decompose-auth [s]
  (let [[h & t] (str/split s #",*\s+")]
    [h (into {} (map #(str/split % #"=" 2) t))]))

(def req
  (merge #:eulalie.request {:body        "{\"TableName\":\"table-name\"}"
                            :endpoint    (url "http://dynamodb.us-east-1.amazonaws.com:80")
                            :headers     {:content-type   "application/x-amz-json-1.0"
                                          :x-amz-target   "DynamoDB_20120810.DescribeTable"
                                          :content-length 26}
                            :service     :dynamo
                            :target      :describe-table
                            :max-retries 0
                            :method      :post
                            :region      :us-east-1}

         #:eulalie.sign    {:eulalie.sign/date    1436795614947
                            :eulalie.sign/creds   {:access-key "rofl" :secret-key "lol a secret key"}
                            :eulalie.sign/service "dynamodb"}))

(def expected-auth
  ["AWS4-HMAC-SHA256"
   {"Credential"    "rofl/20150713/us-east-1/dynamodb/aws4_request"
    "SignedHeaders" "content-length;content-type;host;x-amz-date;x-amz-target"
    "Signature"     "c8f0aee6b2ab806881b5afd93964c774c9bb60d313c08e4b803e5cc021001bac"}])

(t/deftest aws4 []
  (let [auth (-> (sign/aws4 req) :eulalie.request.signed/headers :authorization)]
    (t/is (= expected-auth (decompose-auth auth)))))
