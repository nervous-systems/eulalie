(ns eulalie.service.cognito-test
  (:require [eulalie.service.cognito :as cognito]
            [eulalie.service.cognito.request]
            [eulalie.service.test.util :as test.util]
            [clojure.test.check.clojure-test :as ct]))

(def targets
  [:create-identity-pool
   :delete-identities
   :delete-identity-pool
   :describe-identity
   :describe-identity-pool
   :get-credentials-for-identity
   :get-id
   :get-identity-pool-roles
   :get-open-id-token
   :get-open-id-token-for-developer-identity
   :list-identities
   :list-identity-pools
   :lookup-developer-identity
   :merge-developer-identities
   :set-identity-pool-roles
   :unlink-developer-identity
   :unlink-identity
   :update-identity-pool])

(ct/defspec req+resp 250
  (test.util/request-roundtrip-property "cognito" targets))
