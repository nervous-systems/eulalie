(ns eulalie.sts
  (:require [eulalie.core :as eulalie]
            [cemerick.url :as url]
            [clojure.walk :as walk]
            [eulalie.util :as util]
            [eulalie.platform.xml :as platform.xml]
            [eulalie.util.query :as q]))

(derive :eulalie.service/sts :eulalie.service.generic/xml-response)
(derive :eulalie.service/sts :eulalie.service.generic/query-request)

(def target->seq-spec {})

(def target->elem-spec {})

(def service-name "sts")

(def service-defaults
  {:version "2011-06-15"
   :region "us-east-1"
   :service-name service-name
   :max-retries 3})

(defn assume-role->map [x]
  (let [{{xs :assume-role-with-web-identity-result} :assume-role-with-web-identity-response}
        (walk/prewalk
         (fn [form]
           (cond
             (not (vector? form)) form
             (= (count form) 1) (first form)
             (map? (first form)) (apply merge form)
             :else form))
         x)]
    xs))

(defmethod eulalie/prepare-request :eulalie.service/sts [{:keys [target] :as req}]
  (let [{:keys [body] :as req} (q/prepare-query-request service-defaults req)]
    (assoc req
      :service-name service-name
      :body (q/expand-sequences body (target->seq-spec target)))))

(defmethod eulalie/transform-response-body :eulalie.service/sts
  [{{:keys [target]} :request body :body}]
  (let [elem (platform.xml/string->xml-map body)]
    (cond-> elem (= target :assume-role-with-web-identity) assume-role->map)))
