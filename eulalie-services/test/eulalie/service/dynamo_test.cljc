(ns eulalie.service.dynamo-test
  (:require [eulalie.service.dynamo]
            [eulalie.service.test.util :as test.util]
            [eulalie.service.dynamo.test.common :refer [targets]]
            [clojure.test.check.clojure-test :as ct]))

(ct/defspec req+resp 100
  (test.util/request-roundtrip-property :dynamo targets))
