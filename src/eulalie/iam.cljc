(ns eulalie.iam
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as csk-extras]
            [cemerick.url :as url]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [eulalie.core :as eulalie]
            [eulalie.platform :as platform]
            [eulalie.util :as util]
            [eulalie.util.functor :refer [fmap]]
            [eulalie.platform.xml :as platform.xml]
            [eulalie.util.query :as q]
            [plumbing.core :refer [map-keys]]))

(derive :eulalie.service/iam :eulalie.service.generic/xml-response)
(derive :eulalie.service/iam :eulalie.service.generic/query-request)

(def target->seq-spec {})

(def target->elem-spec {})

(def service-name "iam")

(def service-defaults
  {:version "2011-06-15"
   :region "us-east-1"
   :service-name service-name
   :max-retries 3})

(def case-sensitive? #{"AWS"})

(defn policy-key-out [k]
  (cond (case-sensitive? (name k)) k
        (and (keyword? k) (namespace k))
        (str (str/lower-case (namespace k))
             ":"
             (csk/->PascalCaseString (name k)))
        (string? k) k
        :else (csk/->PascalCaseString k)))

(defn policy-key-in [k]
  (let [k (cond-> k (keyword? k) name)]
    (cond
      (case-sensitive? k) (keyword k)
      (string? k)
      (let [segments (str/split (name k) #":" 2)]
        (apply keyword (map csk/->kebab-case-string segments)))
      :else (csk/->kebab-case-keyword k))))

(defn transform-policy-clause [xform-value {:keys [action effect principal] :as clause}]
  (cond-> clause
    action (assoc :action (fmap xform-value action))
    effect (assoc :effect (xform-value effect))))

(defn transform-policy-statement [xform-value {:keys [statement] :as p}]
  (assoc
   p :statement
   (into []
         (for [clause statement]
           (transform-policy-clause xform-value clause)))))

(defn policy-json-out [policy]
  (->> policy
       (transform-policy-statement policy-key-out)
       (csk-extras/transform-keys policy-key-out)
       platform/encode-json))

(defn policy-json-in [s]
  (->> s
       platform/decode-json
       (csk-extras/transform-keys policy-key-in)
       (transform-policy-statement policy-key-in)))

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

(defmethod eulalie/prepare-request :eulalie.service/iam [{:keys [target] :as req}]
  (let [{:keys [body] :as req} (q/prepare-query-request service-defaults req)]
    (assoc req
      :service-name service-name
      :body (q/expand-sequences body (target->seq-spec target)))))

(defmethod eulalie/transform-response-body :eulalie.service/iam
  [{{:keys [target]} :request body :body}]
  (let [elem (platform.xml/string->xml-map body)]
    (cond-> elem (= target :assume-role-with-web-identity) assume-role->map)))
