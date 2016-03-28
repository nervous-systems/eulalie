(ns eulalie.elastic-transcoder
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [eulalie.core :as eulalie]
            [eulalie.util.service :as util.service]
            [eulalie.util :as util]
            [eulalie.util.json.mapping :as json.mapping]
            [eulalie.util.json :as util.json]
            [eulalie.util.xml :as x]
            [eulalie.platform :as platform]
            [eulalie.platform.xml :as platform.xml]
            [eulalie.util.query :as q]
            #? (:clj
                [clojure.core.async :as async]
                :cljs
                [cljs.core.async :as async])))

(derive :eulalie.service/elastic-transcoder :eulalie.service.generic/json-response)
(derive :eulalie.service/elastic-transcoder :eulalie.service.generic/query-request)

(def service-name "elastictranscoder")
(def service-version "2012-09-25")
(def target-methods {:jobs-by-status :get})

(def service-defaults
  {:version service-version
   :region "us-east-1"
   :service-name service-name
   :max-retries 3})

(defn method-url [{:keys [target body version] :as req} & [path-keys]]
  (let [prefix   ["" (body :version) (csk/->camelCaseString target)]
        segments (into prefix (for [k path-keys]
                                (csk/->PascalCaseString (body k))))
        query (apply dissoc body :version :action path-keys)]
    (-> req
        (assoc-in [:endpoint :path] (str/join "/" segments))
        (assoc-in [:endpoint :query] (into {} (for [[k v] query]
                                                [(csk/->PascalCaseString k) v]))))))

(defmethod eulalie/prepare-request :eulalie.service/elastic-transcoder [{:keys [target] :as req}]
  (let [req
        (q/prepare-query-request service-defaults (merge {:method (target-methods target)} req))
        method-url
        (assoc (method-url req [:status]) :service-name (:service-name service-defaults))]
    method-url))

(defmethod eulalie/transform-request-body :eulalie.service/elastic-transcoder
  [{:keys [body method] :as req}]
  (if (= method :get)
    ""
    (platform/encode-json (util.json/map-request-keys req))))

(defmethod util.json/map-request-keys :eulalie.service/elastic-transcoder [{:keys [body]}]
  body)
