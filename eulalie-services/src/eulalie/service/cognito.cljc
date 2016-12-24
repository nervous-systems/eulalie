(ns eulalie.service.cognito
  (:require [eulalie.service :as service]
            [eulalie.service.generic.json :as generic.json]
            [eulalie.service.cognito.request]
            [eulalie.service.impl.json.mapping :as json.mapping]
            [clojure.set :as set]))

(derive :eulalie.service/cognito :eulalie.service.generic.json/request)
(derive :eulalie.service/cognito :eulalie.service.generic.json/response)

(def ^:private target-prefix
  "com.amazonaws.cognito.identity.model.AWSCognitoIdentityService.")

(def ^:private key-overrides
  {:open-id-connect-provider-arns :OpenIdConnectProviderARNs
   :saml-provider-arns            :SamlProviderARNs
   :role-arn                      :RoleARN})

(def ^:private key-types
  {:cognito-identity-providers :nest
   :rules-configuration        :nest
   :identity-pools             :nest
   :identities                 :nest
   :role-mappings              :attr
   :credentials                :nest
   :unprocessed-identity-ids   :nest})

(defmethod service/defaults :eulalie.service/cognito [_]
  {:region                             "us-east-1"
   :eulalie.sign/service               "cognito-identity"
   :eulalie.service.json/target-prefix target-prefix
   :max-retries                        3})

(defmethod generic.json/map-request-keys :eulalie.service/cognito
  [{:keys [body target]}]
  (json.mapping/transform-request body key-types {:renames key-overrides}))

(defmethod generic.json/map-response-keys :eulalie.service/cognito [{:keys [body]}]
  (json.mapping/transform-response
   body key-types {:renames (set/map-invert key-overrides)}))
