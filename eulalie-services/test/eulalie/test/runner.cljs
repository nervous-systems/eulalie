(ns eulalie.test.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [eulalie.service.cognito-test]
            [eulalie.service.dynamo-test]
            [eulalie.service.dynamo.integration-test]))

(doo-tests
 'eulalie.service.cognito-test
 'eulalie.service.dynamo-test
 'eulalie.service.dynamo.integration-test)
