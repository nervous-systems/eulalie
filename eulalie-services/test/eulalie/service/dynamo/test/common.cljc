(ns eulalie.service.dynamo.test.common)

(def targets
  [:batch-get-item :batch-write-item :create-table :delete-item :delete-table
   :describe-limits :describe-table :get-item :list-tables :put-item
   :query :scan :update-item :update-table])
