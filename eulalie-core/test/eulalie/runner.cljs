(ns eulalie.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [eulalie.instance-data-test]
            [eulalie.sign-test]
            [eulalie.platform-test]
            [eulalie.creds-test]
            [eulalie.impl.platform.crypto-test]))

(doo-tests
 'eulalie.instance-data-test
 'eulalie.sign-test
 'eulalie.platform-test
 'eulalie.impl.platform.crypto-test
 'eulalie.creds-test)
