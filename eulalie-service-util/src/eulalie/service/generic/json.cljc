(ns eulalie.service.generic.json
  (:require [eulalie.service        :as service]
            [eulalie.impl.util      :as util]
            [camel-snake-kebab.core :as csk]
            [eulalie.platform       :as platform]
            [eulalie.impl.service   :as util.service]
            [taoensso.timbre        :as log]
            [clojure.string         :as str]
            [eulalie.service.impl.json.mapping :as json.mapping]))

(defn- req-target [req]
  (let [service    (-> req :eulalie.request/service name)
        service-ns (str "eulalie." service)
        target     (get-in req [(keyword (str service-ns ".request") "body")
                                (keyword service-ns "target")])]
    (str (req :eulalie.json/target-prefix)
         (csk/->PascalCaseString target))))

(defn body->error [body service]
  (when-let [t (some-> (body :__type)
                       not-empty
                       (util/from-last-match "#")
                       csk/->kebab-case-string)]
    {:eulalie.error/type    (keyword (str (namespace service) "." (name service)) t)
     :eulalie.error/message (or (body :Message) (body :message)
                                "Generic: no remote error message")}))

(defmethod service/transform-response-error ::response [resp]
  (let [service (service/resp->service-dispatch resp)]
    (some-> resp :body platform/decode-json (body->error service))))

(defmethod service/prepare-request ::request [req]
  (-> req
      (dissoc :eulalie.service.json/target-prefix)
      (update :eulalie.request/headers
              merge
              {:content-type "application/x-amz-json-1.0"
               :x-amz-target (req-target req)})))

(defmulti map-response-keys service/resp->service-dispatch)
(defmulti map-request-keys  service/req->service-dispatch)

(defn- req->body-key [req]
  (let [k (req :eulalie.request/service)]
    (keyword
     (str/join "." [(namespace k) (name k) "request"])
     "body")))

(defmethod map-request-keys ::request [req]
  (json.mapping/transform-request (req (req->body-key req)) {}))

(defmethod map-response-keys ::response [req]
  (json.mapping/transform-response (req :eulalie.response/body) {}))

(defmethod service/transform-request-body ::request [req]
  (platform/encode-json (map-request-keys req)))

(defmethod service/transform-response-body ::response [resp]
  (map-response-keys
   (update resp :eulalie.response/body platform/decode-json)))
