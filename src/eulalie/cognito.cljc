(ns eulalie.cognito
  (:require [eulalie.core :as eulalie]
            [eulalie.util.json.mapping :as json.mapping]
            [eulalie.util.json :as util.json]
            #? (:clj
                [clojure.core.async :as async]
                :cljs
                [cljs.core.async :as async])))

(derive :eulalie.service/cognito :eulalie.service.generic/json-response)
(derive :eulalie.service/cognito :eulalie.service.generic/json-request)

(def service-name "cognito-identity")
(def target-prefix "com.amazonaws.cognito.identity.model.AWSCognitoIdentityService.")

(def service-defaults
  {:region "us-east-1"
   :service-name service-name
   :target-prefix target-prefix
   :max-retries 3})

(defmethod eulalie/prepare-request :eulalie.service/cognito [req]
  (util.json/prepare-json-request service-defaults req))

(defmethod util.json/map-request-keys :eulalie.service/cognito [{:keys [body]}]
  (json.mapping/transform-request body {}))

(defmethod util.json/map-response-keys :eulalie.service/cognito [{:keys [body]}]
  (json.mapping/transform-response body {}))
