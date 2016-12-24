(ns eulalie.service.generic.json
  (:require [eulalie.service        :as service]
            [eulalie.impl.util      :as util]
            [camel-snake-kebab.core :as csk]
            [eulalie.platform       :as platform]
            [eulalie.impl.service   :as util.service]
            [taoensso.timbre        :as log]
            [#?(:clj clojure.spec :cljs cljs.spec) :as s]
            [eulalie.service.impl.json.mapping :as json.mapping]))

(defn- req-target [prefix {:keys [target]}]
  (str prefix (csk/->PascalCaseString target)))

(defn body->error [body]
  (when-let [t (some-> (body :__type)
                       not-empty
                       (util/from-last-match "#")
                       csk/->kebab-case-keyword)]
    {:type t :message (or (body :Message)
                          (body :message)
                          "Generic: no remote error message")}))

(defmethod service/transform-response-error ::response [resp]
  (some-> resp :body platform/decode-json body->error))

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
  (let [ns     (str "eulalie.service." (name (req :service)) ".request.target")
        target (keyword ns (name target))]
    (if-let [spec (s/get-spec target)]
      (when-not (s/valid? spec body)
        (s/assert spec body)
        (log/warn "Request validation failed for" target (s/explain-data spec body)))
      (log/warn "No spec found for target" target " - skipping validation")))

  (platform/encode-json (map-request-keys req)))

(defmethod service/transform-response-body ::response [{:keys [body] :as resp}]
  (map-response-keys (update resp :body platform/decode-json)))
