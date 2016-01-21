(ns eulalie.test.cognito
  (:require [clojure.walk :as walk]
            [glossop.core #? (:clj :refer :cljs :refer-macros) [go-catching <?]]
            [eulalie.test.common :as test.common #? (:clj :refer :cljs :refer-macros) [deftest is]]
            [eulalie.core :as eulalie]
            [eulalie.cognito]
            [eulalie.util :refer [env!]]
            [plumbing.core :refer [dissoc-in]]))

(def developer-provider-name (env! "COGNITO_DEVELOPER_PROVIDER_NAME"))
(def identity-pool-id (env! "COGNITO_IDENTITY_POOL_ID"))

(defn cognito! [creds target content & [req-overrides]]
  (go-catching
    (let [req (merge
               {:service :cognito
                :target  target
                :max-retries 0
                :body content
                :creds creds}
               req-overrides)]
      (:body (<? (test.common/issue-raw! req))))))

(deftest get-token-test
  (test.common/with-aws
    (fn [creds]
      (go-catching
        (let [{:keys [token identity-id]}
              (<? (cognito!
                   creds
                   :get-open-id-token-for-developer-identity
                   {:identity-pool-id identity-pool-id
                    :logins {developer-provider-name "test@unclipapp.com"}
                    :token-duration 86400}))]
          (is (string? token))
          (is (string? identity-id)))))))
