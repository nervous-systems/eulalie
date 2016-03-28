(ns eulalie.test.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [cljs.test]
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
            [eulalie.test.cognito-sync]
            [eulalie.test.elastic-transcoder]))

(doo-tests
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
 'eulalie.test.cognito-sync
 'eulalie.test.elastic-transcoder)
