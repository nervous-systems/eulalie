(ns eulalie.elastic-transcoder
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [eulalie.core :as eulalie]
            [eulalie.util.service :as util.service]
            [eulalie.util :as util]
            [eulalie.util.json.mapping :as json.mapping]
            [eulalie.util.json :as util.json]
            [eulalie.platform :as platform]
            [eulalie.util.query :as q]
            #? (:clj
                [clojure.core.async :as async]
                :cljs
                [cljs.core.async :as async])))

(derive :eulalie.service/elastic-transcoder :eulalie.service.generic/json-response)
(derive :eulalie.service/elastic-transcoder :eulalie.service.generic/query-request)

(def service-name "elastictranscoder")
(def service-version "2012-09-25")
(def service-defaults
  {:version service-version
   :region "us-east-1"
   :service-name service-name
   :max-retries 3})

(defn method-url [{:keys [method target body version] :as req} & [path-keys]]
  (let [prefix   ["" (body :version) (csk/->camelCaseString target)]
        path-key (some body path-keys)
        segments (cond-> prefix path-key (conj (name path-key)))
        query    (apply dissoc body :version :action path-keys)]
    (-> req
        (assoc-in [:endpoint :path] (str/join "/" segments))
        (cond-> (not= method :post)
          (assoc-in [:endpoint :query] (into {} (for [[k v] query]
                                                  [(csk/->PascalCaseString k) v])))))))

(defmethod eulalie/prepare-request :eulalie.service/elastic-transcoder [{:keys [target] :as req}]
  (let [req (cond-> req
              (= target :jobs-by-status) (update-in [:body :status] csk/->PascalCaseString))
        req (q/prepare-query-request service-defaults (merge {:method :get} req))]
    (assoc (method-url req #{:status :pipeline :preset})
      :service-name (service-defaults :service-name))))

(defmethod eulalie/transform-request-body :eulalie.service/elastic-transcoder
  [{:keys [body method] :as req}]
  (if (= method :get)
    ""
    (platform/encode-json (util.json/map-request-keys req))))

(def nested-keys
  (zipmap
   #{:input :outputs :captions :playlists :encryption :album-art
     :composition :caption-formats :pipelines :notifications :thumbnail-config
     :thumbnails :content-config :presets :codec-options :video :audio :watermarks}
   (repeat :nest)))

(def pascal-case-enums
  #{:album-art-merge :merge-policy :bit-order :audio-packing-mode :sizing-policy
    :horizontal-align :target :padding-policy :type :vertical-align})

(def request-mapping
  (merge
   nested-keys
   (zipmap pascal-case-enums (repeat #(some-> % csk/->PascalCaseString)))
   {:mode       (fn [k] (some-> k name str/upper-case))}))

(def response-mapping
  (merge
   nested-keys
   (zipmap pascal-case-enums (repeat #(some-> % csk/->kebab-case-keyword)))
   {:mode (fn [k] (some-> k str/lower-case keyword))
    :container  keyword
    :codec      keyword}))

(defmethod util.json/map-request-keys :eulalie.service/elastic-transcoder [{:keys [body]}]
  (json.mapping/transform-request
   body
   (assoc request-mapping
     :video (fn [m]
              (json.mapping/transform-request
               m (assoc request-mapping :id csk/->PascalCaseString))))))

(defmethod util.json/map-response-keys :eulalie.service/elastic-transcoder [{:keys [body]}]
  (json.mapping/transform-response
   body
   (assoc response-mapping
     :video (fn [m]
              (json.mapping/transform-response
               m (assoc response-mapping :id csk/->kebab-case-keyword))))))
