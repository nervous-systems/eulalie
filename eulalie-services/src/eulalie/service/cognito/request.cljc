(ns ^:no-doc eulalie.service.cognito.request
  (:require [#?(:clj clojure.spec :cljs cljs.spec) :as s]
            [#?(:clj clojure.spec.gen :cljs cljs.spec.impl.gen) :as gen]))

(defn- string* [chars min-len & [max-len]]
  (let [regex (re-pattern (str "(?i)" chars "{" min-len "," max-len "}"))]
   (s/with-gen (fn [s]
                 (and (string? s) (re-matches regex s)))
     (fn []
       (gen/fmap
        #(apply str %)
        (gen/vector (gen/char-alphanumeric min-len max-len)))))))

(s/def ::client-id          string?)
(s/def ::provider-name      (string* "[\\w._-]" 1  128))
(s/def ::arn                (string* "."        20 2048))
(s/def ::identity-pool-name (string* "[\\w ]"   1  128))
(s/def ::allow-unauthenticated-identities boolean?)
(s/def ::developer-provider-name ::provider-name)
(s/def ::user-id       string?)
(s/def ::claim         string?)
(s/def ::match-type    string?)
(s/def ::value         string?)

(s/def ::cognito-identity-provider
  (s/keys :opt-un [::client-id ::provider-name]))

(s/def ::cognito-identity-providers
  (s/* ::cognito-identity-provider))

(s/def ::open-id-connect-provider-arns (s/* ::arn))
(s/def ::saml-provider-arns            (s/* ::arn))

(s/def ::supported-login-providers (s/map-of ::provider-name string?))

(s/def :eulalie.service.cognito.request.target/create-identity-pool
  (s/keys :req-un [::allow-unauthenticated-identities
                   ::identity-pool-name]
          :opt-un [::cognito-identity-providers
                   ::developer-provider-name
                   ::open-id-connect-provider-arns
                   ::saml-provider-arns
                   ::supported-login-providers]))

(s/def ::identity-id string?)
(s/def ::identity-ids-to-delete (s/+ ::identity-id))
(s/def :eulalie.service.cognito.request.target/delete-identities
  (s/keys :req-un [::identity-ids-to-delete]))

(s/def ::identity-pool-id string?)

(s/def :eulalie.service.cognito.request.target/delete-identity-pool
  (s/keys :req-un [::identity-pool-id]))

(s/def :eulalie.service.cognito.request.target/describe-identity
  (s/keys :req-un [::identity-id]))

(s/def :eulalie.service.cognito.request.target/describe-identity-pool
  (s/keys :req-un [::identity-pool-id]))

(s/def ::logins (s/map-of string? string?))
(s/def ::custom-role-arn ::arn)

(s/def :eulalie.service.cognito.request.target/get-credentials-for-identity
  (s/keys :req-un [::identity-id]
          :opt-un [::custom-role-arn ::logins]))

(s/def ::account-id string?)

(s/def :eulalie.service.cognito.request.target/get-id
  (s/keys :req-un [::identity-pool-id]
          :opt-un [::logins ::account-id]))

(s/def :eulalie.service.cognito.request.target/get-identity-pool-roles
  (s/keys :req-un [::identity-pool-id]))

(s/def :eulalie.service.cognito.request.target/get-open-id-token
  (s/keys :req-un [::identity-id]
          :opt-un [::logins]))

(s/def ::token-duration (s/int-in 1 86400))

(s/def :eulalie.service.cognito.request.target/get-open-id-token-for-developer-identity
  (s/keys :req-un [::identity-pool-id ::logins]
          :opt-un [::identity-id ::token-duration]))

(s/def ::hide-disabled    boolean?)
(s/def ::max-results      (s/int-in 1 60))
(s/def ::next-token       (s/nilable (string* "\\S" 1)))

(s/def :eulalie.service.cognito.request.target/list-identities
  (s/keys :req-un [::identity-pool-id ::max-results]
          :opt-un [::hide-disabled ::next-token]))

(s/def :eulalie.service.cognito.request.target/list-identity-pools
  (s/keys :req-un [::max-results]
          :opt-un [::next-token]))

(s/def ::developer-user-identifier ::user-id)

(s/def :eulalie.service.cognito.request.target/lookup-developer-identity
  (s/keys :req-un [::identity-pool-id]
          :opt-un [::developer-user-identifier ::identity-id ::next-token ::max-results]))

(s/def ::destination-user-identifier ::user-id)
(s/def ::source-user-identifier      ::user-id)

(s/def :eulalie.service.cognito.request.target/merge-developer-identities
  (s/keys :req-un [::destination-user-identifier
                   ::developer-provider-name
                   ::identity-pool-id
                   ::source-user-identifier]))

(s/def ::role-arn ::arn)
(s/def ::roles    (s/map-of string? string?))
(s/def ::type     string?)
(s/def ::rule     (s/keys :req-un [::claim ::match-type ::role-arn ::value]))
(s/def ::rules    (s/every ::rule :min-count 1 :max-count 25))

(s/def ::ambiguous-role-resolution string?)
(s/def ::rules-configuration (s/keys :req-un [::rules]))

(s/def ::role-mapping
  (s/keys :req-un [::type]
          :opt-un [::ambiguous-role-resolution
                   ::rules-configuration
                   ::rules]))

(s/def ::role-mappings (s/map-of string? ::role-mapping))

(s/def :eulalie.service.cognito.request.target/set-identity-pool-roles
  (s/keys :req-un [::identity-pool-id ::roles]
          :opt-un [::role-mappings]))

(s/def :eulalie.service.cognito.request.target/unlink-developer-identity
  (s/keys :req-un [::developer-provider-name
                   ::developer-user-identifier
                   ::identity-id
                   ::identity-pool-id]))

(s/def ::logins-to-remove (s/* string?))

(s/def :eulalie.service.cognito.request.target/unlink-identity
  (s/keys :req-un [::identity-id ::logins ::logins-to-remove]))

(s/def :eulalie.service.cognito.request.target/update-identity-pool
  (s/keys :req-un [::allow-unauthenticated-identities
                   ::identity-pool-id
                   ::identity-pool-name]
          :opt-un [::cognito-identity-providers
                   ::developer-provider-name
                   ::open-id-connect-provider-arns
                   ::saml-provider-arns
                   ::supported-login-providers]))
