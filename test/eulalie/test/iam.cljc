(ns eulalie.test.iam
  (:require [eulalie.iam :as iam]
            [eulalie.platform :refer [encode-json]]
            [eulalie.test.common :as test.common]
            [eulalie.util :refer [env!]]
            #? (:clj [clojure.test :refer [deftest is]]
                :cljs [cljs.test :refer-macros [deftest is]])))

(def policies
  [[{:statement
     [{:sid "fink-nottle-sqs-sns-bridge",
       :effect :allow
       :principal {:AWS "*"}
       :action [:sqs/send-message],
       :resource "url",
       :condition {:arn-equals {:aws/source-arn "arn"}}}]}
    {"Statement"
     [{"Sid" "fink-nottle-sqs-sns-bridge",
       "Effect" "Allow",
       "Principal" {"AWS" "*"},
       "Action" ["sqs:SendMessage"],
       "Resource" "url",
       "Condition" {"ArnEquals" {"aws:SourceArn" "arn"}}}]}]
   [{:version "2012-10-17",
     :statement
     [{:action [:cloudwatch/delete-alarms,
                :dynamodb/*],
       :effect :allow,
       :resource "*",
       :sid "DDBConsole"},
      {:effect :allow,
       :action [:iam/get-role-policy,
                :iam/pass-role],
       :resource ["arn:aws:execute-api:*:*:*"],
       :sid "IAMEDPRoles"}]}
    {"Version" "2012-10-17",
     "Statement"
     [{"Action" ["cloudwatch:DeleteAlarms",
                 "dynamodb:*"],
       "Effect" "Allow",
       "Resource" "*",
       "Sid" "DDBConsole"},
      {"Effect" "Allow",
       "Action" ["iam:GetRolePolicy",
                 "iam:PassRole"],
       "Resource" ["arn:aws:execute-api:*:*:*"],
       "Sid" "IAMEDPRoles"}]}]
   [{:version "2012-10-17",
     :statement
     [{:sid "Stmt1453400272000",
       :effect :deny,
       :action [:aws-portal/view-account],
       :condition
       {:not-ip-address {:aws/user-id "123.123.123.123"}},
       :resource ["*"]}
      {:sid "Stmt1453400455000",
       :effect :deny,
       :action [:iot/create-thing],
       :condition {:string-equals {:aws/source-vpc "JACKANORY"}},
       :resource ["*"]}]}
    {"Version" "2012-10-17",
     "Statement"
     [{"Sid" "Stmt1453400272000",
       "Effect" "Deny",
       "Action" ["aws-portal:ViewAccount"],
       "Condition" {"NotIpAddress" {"aws:userid" "123.123.123.123"}},
       "Resource" ["*"]},
      {"Sid" "Stmt1453400455000",
       "Effect" "Deny",
       "Action" ["iot:CreateThing"],
       "Condition" {"StringEquals" {"aws:sourceVpc" "JACKANORY"}},
       "Resource" ["*"]}]}]
   ])

(deftest policy-json-out
  (doseq [[input output] policies]
    (is (= (encode-json output) (iam/policy-json-out input)))))

(deftest policy-json-in
  (doseq [[output input] policies]
    (is (= output (iam/policy-json-in (encode-json input))))))
