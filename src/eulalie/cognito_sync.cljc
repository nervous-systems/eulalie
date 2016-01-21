(ns eulalie.cognito-sync
  (:require [eulalie.core :as eulalie]
            [eulalie.util.json.mapping :as json.mapping]
            [eulalie.util.json :as util.json]
            #? (:clj
                [clojure.core.async :as async]
                :cljs
                [cljs.core.async :as async])))

(derive :eulalie.service/cognito-sync :eulalie.service.generic/json-response)
(derive :eulalie.service/cognito-sync :eulalie.service.generic/json-request)

(def service-name "cognito-sync")
(def target-prefix "com.amazonaws.cognito.sync.model.AWSCognitoSyncService.")

(def service-defaults
  {:region "us-east-1"
   :service-name service-name
   :target-prefix target-prefix
   :max-retries 3})

(defmethod eulalie/prepare-request :eulalie.service/cognito-sync [req]
  (util.json/prepare-json-request service-defaults req))

(defmethod util.json/map-request-keys :eulalie.service/cognito-sync [{:keys [body]}]
  (json.mapping/transform-request body {:record-patches :nest}))

(defmethod util.json/map-response-keys :eulalie.service/cognito-sync [{:keys [body]}]
  (json.mapping/transform-response
   body {:records  :nest
         :datasets :nest
         :dataset  :nest
         :type     :nest}))
