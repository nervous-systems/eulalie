(ns eulalie.service.dynamo-test
  (:require [eulalie.service.dynamo :as dynamo]
            [eulalie.service.dynamo.request]
            [eulalie.service.test.util :as test.util]
            [eulalie.service.impl.json.mapping :as json.mapping]
            [clojure.test.check.clojure-test :as ct]
            [clojure.walk :as prewalk]
            [clojure.walk :as walk]))

(def targets
  [:batch-get-item :batch-write-item :create-table :delete-item :delete-table
   :describe-limits :describe-table :get-item :list-tables :put-item
   :query :scan :update-item :update-table])

(ct/defspec req+resp 100
  (test.util/request-roundtrip-property :dynamo targets))
