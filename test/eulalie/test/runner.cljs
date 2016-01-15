(ns eulalie.test.runner
  (:require [doo.runner :refer-macros [doo-tests]]
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

(enable-console-print!)

(doo-tests
 'eulalie.test.core
 'eulalie.test.creds
 'eulalie.test.dynamo
 'eulalie.test.dynamo.mapping
 'eulalie.test.dynamo.transform
 'eulalie.test.dynamo-streams
 'eulalie.test.instance-data
 'eulalie.test.sign
 'eulalie.test.sns
 'eulalie.test.sqs)
