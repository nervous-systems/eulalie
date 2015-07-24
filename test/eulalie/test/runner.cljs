(ns eulalie.test.runner
  (:require [cljs.test]
            [eulalie.test.core]
            [eulalie.test.creds]
            [eulalie.test.dynamo]
            [eulalie.test.dynamo.mapping]
            [eulalie.test.dynamo.transform]
            [eulalie.test.dynamo-streams]
            [eulalie.test.instance-data]
            [eulalie.test.sign]
            [eulalie.test.sns]
            [eulalie.test.sqs]))

(defn run []
  (cljs.test/run-tests
   'eulalie.test.core
   'eulalie.test.creds
   'eulalie.test.dynamo
   'eulalie.test.dynamo.mapping
   'eulalie.test.dynamo.transform
   'eulalie.test.dynamo-streams
   'eulalie.test.instance-data
   'eulalie.test.sign
   'eulalie.test.sns
   'eulalie.test.sqs))

(enable-console-print!)

(set! *main-cli-fn* run)
