(ns eulalie.test.cognito-util
  (:require [glossop.core #? (:clj :refer :cljs :refer-macros) [go-catching <?]]
            [eulalie.test.common #? (:clj :refer :cljs :refer-macros) [deftest is]]
            [eulalie.core :as eulalie]
            [eulalie.cognito]
            [eulalie.cognito.util :refer [get-open-id-token-for-developer-identity!]]
            [eulalie.test.common :as test.common]
            [eulalie.util :refer [env!]]))

(def developer-provider-name (env! "COGNITO_DEVELOPER_PROVIDER_NAME"))
(def identity-pool-id (env! "COGNITO_IDENTITY_POOL_ID"))

(deftest get-token-test
  (test.common/with-aws
    (fn [creds]
      (go-catching
       (let [{:keys [token identity-id]}
             (<? (get-open-id-token-for-developer-identity!
                  creds
                  identity-pool-id
                  {developer-provider-name "test@unclipapp.com"}))]
         (is (string? token))
         (is (string? identity-id)))))))

(deftest get-token-test-existing-user
  (test.common/with-aws
    (fn [creds]
      (go-catching
       (let [logins {developer-provider-name "test@unclipapp.com"}
             {identity-id-1 :identity-id token-1 :token}
             (<? (get-open-id-token-for-developer-identity!
                  creds
                  identity-pool-id
                  logins))
             {identity-id-2 :identity-id token-2 :token}
             (<? (get-open-id-token-for-developer-identity!
                  creds
                  identity-pool-id
                  logins
                  {:identity-id identity-id-1}))]
         (is (string? identity-id-1))
         (is (= identity-id-1 identity-id-2))
         (is (string? token-1))
         (is (string? token-2)))))))
