(ns eulalie.test.dynamo.mapping
  (:require [eulalie.util.json.mapping :as mapping]
            [eulalie.test.dynamo.test-data :as test-data]
            [eulalie.dynamo.key-types :as key-types]
            [eulalie.util]
            #? (:clj
                [clojure.test :refer [deftest is]]
                :cljs
                [cljs.test :refer-macros [deftest is]])))

(deftest request-transform-all
  (let [actual (mapping/transform-request
                test-data/all-request-keys key-types/request-key-types)]
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
               :projection-expression [:#foo-bar]}}}
            key-types/request-key-types)))))

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
               {:put-request    {:item p-item}}]}}
            key-types/request-key-types)))))

(deftest response-transform-all
  (let [actual (mapping/transform-response
                test-data/all-response-keys key-types/response-key-types)]
    (is (= actual test-data/all-response-keys-in))))
