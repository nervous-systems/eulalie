(ns ^:no-doc eulalie.sign
  (:require [eulalie.request]
            [eulalie.impl.sign :as util.sign]
            [eulalie.impl.util :as util :refer [assoc-when]]
            [eulalie.impl.platform.time :as platform.time]
            [eulalie.impl.platform :as platform]
            [eulalie.impl.platform.crypto :as platform.crypto]
            [clojure.string :as str]
            [cemerick.url :as url]
            [#?(:clj clojure.spec :cljs cljs.spec) :as s]))

(def ^:private KEY-PREFIX   "AWS4")
(def ^:private MAGIC-SUFFIX "aws4_request")
(def ^:private ALGORITHM    "AWS4-HMAC-SHA256")

(defn- signature-date [offset-msecs]
  (platform.time/from-long (- (platform.time/msecs-now) offset-msecs)))

(defn- request-scope [req]
  (let [date-stamp (platform.time/->aws-date (req :eulalie.sign/date))]
    (str/join "/" [date-stamp
                   (name (req :eulalie.request/region))
                   (req ::service)
                   MAGIC-SUFFIX])))

(defn- canonical-request
  [{:keys [eulalie.request/endpoint
           eulalie.sign/query-payload?
           eulalie.request/headers] :as req}]
  (let [query   (when-not query-payload?
                  (some-> req :eulalie.request/query url/map->query))
        headers (into (sorted-map) (map util.sign/canonical-header) headers)
        method  (-> req :eulalie.request/method name str/upper-case)
        path    (-> (:path endpoint) not-empty (or "/"))]
    [(keys headers)
     (util.sign/newline-join
      method
      path
      (:query endpoint)
      (str/join "\n" (util/join-each ":" headers))
      ""
      (str/join ";" (keys headers))
      (req ::hash))]))

(defn- signable-string [req]
  (util.sign/newline-join
   ALGORITHM
   (platform.time/->aws-date-time (req :eulalie.sign/date))
   (req ::scope)
   (platform.crypto/str->sha256 (req ::canonical))))

(defn- sign-hms256 [s k]
  (platform.crypto/hmac-sha256
   (platform/utf8-bytes s)
   k))

(defn- sign [req]
  (->> req :eulalie.sign/creds :secret-key (str KEY-PREFIX)
       platform/utf8-bytes
       (sign-hms256 (-> req :eulalie.sign/date platform.time/->aws-date))
       (sign-hms256 (-> req :eulalie.request/region name))
       (sign-hms256 (req ::service))
       (sign-hms256 MAGIC-SUFFIX)
       (sign-hms256 (signable-string req))
       platform/bytes->hex-str))

(defn- signed-authorization [req]
  (let [scope               (request-scope req)
        [headers canon-req] (canonical-request req)]
    (str
     ALGORITHM " "
     "Credential="    (-> req :eulalie.sign/creds :access-key (str "/" scope)) ", "
     "SignedHeaders=" (str/join ";" headers) ", "
     "Signature="     (sign (assoc req ::canonical canon-req ::scope scope)))))

(defn- date [req]
  (or (some-> req :eulalie.sign/date platform.time/from-long)
      (signature-date (req :eulalie.error/time-offset 0))))

(defn- required-headers [{:keys [eulalie.request/endpoint
                                 eulalie.sign/creds] :as req}]
  (assoc-when
   {:x-amz-date (platform.time/->aws-date-time (date req))
    :host       (util.sign/host-header endpoint)}
   :x-amz-security-token (creds :token)))

(defn aws4
  [{:keys [eulalie.request/body ::service] :as req}]
  (let [headers (required-headers req)
        req     (-> req
                    (update ::creds util.sign/sanitize-creds)
                    (update :eulalie.request/headers merge headers)
                    (assoc  :eulalie.sign/date (date req)))
        hash    (platform.crypto/str->sha256 body)
        auth    (signed-authorization (assoc req ::hash hash))]
    (assoc req
      :eulalie.request.signed/headers
      (merge (req :eulalie.request/headers)
             {:authorization        auth
              :x-amz-content-sha256 hash}))))

(s/fdef aws4
  :args (s/cat :req :eulalie.request/signable)
  :ret  :eulalie.request/signed)
