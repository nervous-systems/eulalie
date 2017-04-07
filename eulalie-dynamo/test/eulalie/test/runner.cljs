(ns eulalie.test.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [eulalie.dynamo-test]
            [eulalie.dynamo.integration-test]))

(doo-tests
 'eulalie.dynamo-test
 'eulalie.dynamo.integration-test)
