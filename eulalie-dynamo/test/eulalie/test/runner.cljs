(ns eulalie.test.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [eulalie.service.dynamo-test]
            [eulalie.service.dynamo.integration-test]))

(doo-tests
 'eulalie.service.dynamo-test
 'eulalie.service.dynamo.integration-test)
