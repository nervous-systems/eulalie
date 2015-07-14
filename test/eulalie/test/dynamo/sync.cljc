(ns eulalie.test.dynamo.sync
  (:require [eulalie.core :as eulalie]
            [eulalie.dynamo]
            #? (:clj
                [clojure.test :refer [deftest is]]))
  #? (:cljs (:require-macros [cemerick.cljs.test :refer [deftest is]])))

(def table :eulalie-test-table)

(deftest transform-body
  (is (= (str "{\"TableName\":\"" (name table) "\"}")
         (eulalie/transform-request-body
          {:service :dynamo :body {:table-name table}}))))

(deftest transform-response-body
  (is (= {:table-name table}
         (eulalie/transform-response-body
          {:request {:service :dynamo}
           :body (str "{\"TableName\":\"" (name table) "\"}")}))))

(deftest transform-body-unicode
  (is (= "{\"TableName\":\"\u00a5123Hello\"}"
         (eulalie/transform-request-body
          {:service :dynamo :body {:table-name "\u00a5123Hello"}}))))

(deftest transform-response-body-unicode
  (is (= {:table-name (keyword "\u00a5123Hello")}
         (eulalie/transform-response-body
          {:request {:service :dynamo}
           :body "{\"TableName\":\"\u00a5123Hello\"}"}))))
