(ns eulalie.service.generic.json
  (:require [eulalie.service        :as service]
            [eulalie.impl.util      :as util]
            [camel-snake-kebab.core :as csk]
            [eulalie.platform       :as platform]
            [eulalie.impl.service   :as util.service]
            [taoensso.timbre        :as log]
            [eulalie.service.impl.json.mapping :as json.mapping]))

(defn- req-target [prefix {:keys [target]}]
  (str prefix (csk/->PascalCaseString target)))

(defn body->error [body service]
  (when-let [t (some-> (body :__type)
                       not-empty
                       (util/from-last-match "#")
                       csk/->kebab-case-string)]
    {:eulalie.error/type (keyword (str (namespace service) "." (name service)) t)
     :eulalie.error/message (or (body :Message) (body :message)
                                "Generic: no remote error message")}))

(defmethod service/transform-response-error ::response [resp]
  (let [service (service/resp->service-dispatch resp)]
    (some-> resp :body platform/decode-json (body->error service))))

(defmethod service/prepare-request ::request
  [{:keys [:eulalie.service.json/target-prefix] :as req}]
  (-> req
      (dissoc :eulalie.service.json/target-prefix)
      (update :headers merge
              {:content-type "application/x-amz-json-1.0"
               :x-amz-target (req-target target-prefix req)})))

(defmulti map-response-keys service/resp->service-dispatch)
(defmulti map-request-keys  service/req->service-dispatch)

(defmethod map-request-keys ::request [{:keys [body]}]
  (json.mapping/transform-request body {}))

(defmethod map-response-keys ::response [{:keys [body]}]
  (json.mapping/transform-response body {}))

(defmethod service/transform-request-body ::request [{:keys [target body] :as req}]
  (platform/encode-json (map-request-keys req)))

(defmethod service/transform-response-body ::response [{:keys [body] :as resp}]
  (map-response-keys (update resp :body platform/decode-json)))
