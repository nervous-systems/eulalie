(ns eulalie.util.json
  (:require [eulalie]
            [eulalie.util :as util]
            [eulalie.util.json.mapping :as json.mapping]
            [camel-snake-kebab.core :as csk]
            [eulalie.service-util :as service-util]
            [cheshire.core :as cheshire]))

(defn body->error [{:keys [__type message Message]}]
  (when-let [t (some-> __type
                       not-empty
                       (util/from-last-match "#")
                       csk/->kebab-case-keyword)]
    {:type t :message (or Message message)}))

(defmethod eulalie/transform-response-error
  :eulalie.service.generic/json-response [resp]
  (some-> resp :body (cheshire/decode true) body->error))

(defn req-target [prefix {:keys [target]}]
  (str prefix (service-util/->camel-s target)))

(defn prepare-json-request
  [{:keys [service-name target-prefix] :as service-defaults} req]
  (let [req (service-util/default-request service-defaults req)]
    (-> req
        (update-in [:headers] merge
                   {:content-type "application/x-amz-json-1.0"
                    :x-amz-target (req-target target-prefix req)})
        (assoc :service-name service-name))))

(defmulti  map-response-keys eulalie/resp->service-dispatch)
(defmulti  map-request-keys  eulalie/req->service-dispatch)

(defmethod eulalie/transform-response-body
  :eulalie.service.generic/json-response
  [{:keys [body] :as resp}]
  (map-response-keys
   (assoc resp :body (cheshire/decode body true))))

(defmethod eulalie/transform-request-body
  :eulalie.service.generic/json-request
  [req]
  (cheshire/encode (map-request-keys req)))
