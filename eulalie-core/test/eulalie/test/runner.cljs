(ns eulalie.test.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [eulalie.instance-data-test]
            [eulalie.sign-test]
            [eulalie.platform-test]
            [eulalie.creds-test]
            [eulalie.core-test]))

(doo-tests
 'eulalie.instance-data-test
 'eulalie.sign-test
 'eulalie.platform-test
 'eulalie.core-test
 'eulalie.creds-test)
