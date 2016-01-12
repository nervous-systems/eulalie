(ns eulalie.test.runner
  (:require [cljs.test]
            [eulalie.test.core]
            [eulalie.test.creds]
            [eulalie.test.dynamo]
            [eulalie.test.dynamo.mapping]
            [eulalie.test.dynamo.transform]
            [eulalie.test.dynamo-streams]
            [eulalie.test.instance-data]
            [eulalie.test.ses]
            [eulalie.test.sign]
            [eulalie.test.sns]
            [eulalie.test.sqs]
            [eulalie.test.sts]
            [eulalie.test.cognito]
            [eulalie.test.cognito-sync]))

(defn run []
  (cljs.test/run-tests
   'eulalie.test.core
   'eulalie.test.creds
   'eulalie.test.dynamo
   'eulalie.test.dynamo.mapping
   'eulalie.test.dynamo.transform
   'eulalie.test.dynamo-streams
   'eulalie.test.instance-data
   'eulalie.test.ses
   'eulalie.test.sign
   'eulalie.test.sns
   'eulalie.test.sqs
   'eulalie.test.sts
   'eulalie.test.cognito
   'eulalie.test.cognito-sync))

(enable-console-print!)

(set! *main-cli-fn* run)
