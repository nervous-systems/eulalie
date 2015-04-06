(ns eulalie.dynamo.mapping-test
  (:require [eulalie.dynamo.mapping :as mapping]
            [eulalie.dynamo.test-data :as test-data]
            [eulalie.util :refer :all]
            [clojure.data :as data]
            [clojure.test :refer :all]))

(deftest request-transform-all
  (let [actual (mapping/transform-request  test-data/all-request-keys)]
    (is (= actual test-data/all-request-keys-out))))

(deftest request-items-batch-get
  (let [ks [{:key-one {:M {:conditional-operator {:S "Str"}}}}
            {:key-one {:L [{:SS ["String set" " "]}]}}]
        attr-names {:#BazQuux :BazQuux, :#foo-bar :foo-bar}]
    (is (= {:RequestItems
            {:LONGER-table-name!
             {:ProjectionExpression "#foo-bar"
              :ConsistentRead false
              :ExpressionAttributeNames attr-names
              :AttributesToGet [:all-attributes :BazQuux]
              :Keys ks}}}
           (mapping/transform-request
            {:request-items
             {:LONGER-table-name!
              {:attributes-to-get [:all-attributes :BazQuux]
               :consistent-read false
               :expression-attribute-names attr-names
               :keys ks
               :projection-expression [:#foo-bar]}}})))))

(deftest request-items-batch-write
  (let [p-item {:ns-key  {:NS #{1}}
                :the-map {:M  {:request-items {:S " "}}}}
        d-item {:ns-key {:NS #{1 3 2}}}]

    (is (= {:RequestItems
            {:really-short-table-name
             [{:DeleteRequest {:Key d-item}}
              {:PutRequest    {:Item p-item}}]}}
           (mapping/transform-request
            {:request-items
             {:really-short-table-name
              [{:delete-request {:key  d-item}}
               {:put-request    {:item p-item}}]}})))))

(deftest response-transform-all
  (let [actual (mapping/transform-response test-data/all-response-keys)]
    (is (= actual test-data/all-response-keys-in))))
