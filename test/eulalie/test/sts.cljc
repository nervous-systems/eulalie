(ns eulalie.test.sts
  (:require [eulalie.core :as eulalie]
            [eulalie.sts]
            [eulalie.test.common :as test.common #? (:clj :refer :cljs :refer-macros) [deftest is]]
            [eulalie.util :refer [env!]]
            [eulalie.cognito.util :refer [get-open-id-token-for-developer-identity!]]
            [glossop.core #? (:clj :refer :cljs :refer-macros) [go-catching <?]]))

(def cognito-developer-provider-name (env! "COGNITO_DEVELOPER_PROVIDER_NAME"))
(def cognito-identity-pool-id (env! "COGNITO_IDENTITY_POOL_ID"))
(def cognito-role-arn (env! "COGNITO_ROLE_ARN"))

(deftest test-assume-role-with-web-identity!
  (test.common/with-aws
    (fn [creds]
      (go-catching
        (let [{:keys [token]}
              (<? (get-open-id-token-for-developer-identity!
                   creds
                   cognito-identity-pool-id
                   {cognito-developer-provider-name "noone@nowhere.com"}
                   86400))
              {{:keys [access-key-id secret-access-key session-token]} :credentials}
              (<? (test.common/sts!
                   creds
                   :assume-role-with-web-identity
                   {:role-arn cognito-role-arn
                    :web-identity-token token
                    :role-session-name "web-identity"}))]
          (is (string? access-key-id))
          (is (string? secret-access-key))
          (is (string? session-token)))))))
