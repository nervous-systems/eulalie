(ns eulalie.test.iam
  (:require [eulalie.iam :as iam]
            [eulalie.platform :refer [encode-json]]
            [eulalie.test.common :as test.common]
            [eulalie.util :refer [env!]]
            #? (:clj [clojure.test :refer [deftest is]]
                :cljs [cljs.test :refer-macros [deftest is]])))

(def policy-in {:statement
                [{:sid "fink-nottle-sqs-sns-bridge",
                  :effect :allow
                  :principal {:AWS "*"}
                  :action [:sqs/send-message],
                  :resource "url",
                  :condition {:arn-equals {:aws/source-arn "arn"}}}]})

(def policy-out {"Statement"
                 [{"Sid" "fink-nottle-sqs-sns-bridge",
                   "Effect" "Allow",
                   "Principal" {"AWS" "*"},
                   "Action" ["sqs:SendMessage"],
                   "Resource" "url",
                   "Condition" {"ArnEquals" {"aws:SourceArn" "arn"}}}]})

(deftest policy-json-out
  (let [input policy-in
        output (encode-json policy-out)]
    (is (= output (iam/policy-json-out input)))))

(deftest policy-json-in
  (is (= policy-in (iam/policy-json-in (encode-json policy-out)))))
