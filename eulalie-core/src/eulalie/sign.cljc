(ns eulalie.sign
  (:require [eulalie.impl.sign :as util.sign]
            [eulalie.impl.util :as util :refer [assoc-when]]
            [eulalie.impl.platform.time :as platform.time]
            [eulalie.impl.platform :as platform]
            [eulalie.impl.platform.crypto :as platform.crypto]
            [clojure.string :as str]
            [cemerick.url :as url]))

(def ^:private KEY-PREFIX   "AWS4")
(def ^:private MAGIC-SUFFIX "aws4_request")
(def ^:private ALGORITHM    "AWS4-HMAC-SHA256")

(defn- signature-date [offset-msecs]
  (platform.time/from-long (- (platform.time/msecs-now) offset-msecs)))

(defn- request-scope [req]
  (let [date-stamp (platform.time/->aws-date (req :date))]
    (str/join "/" [date-stamp (name (req :region)) (req ::service) MAGIC-SUFFIX])))

(defn- canonical-request
  [{:keys [endpoint query-payload? headers] :as req}]
  (let [query   (when-not query-payload?
                  (some-> req :query url/map->query))
        headers (into (sorted-map) (map util.sign/canonical-header) headers)
        method  (-> req :method name str/upper-case)
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
   (platform.time/->aws-date-time (req :date))
   (req ::scope)
   (platform.crypto/str->sha256 (req ::canonical))))

(defn- sign-hms256 [s k]
  (platform.crypto/hmac-sha256
   (platform/utf8-bytes s)
   k))

(defn- sign [req]
  (->> req :creds :secret-key (str KEY-PREFIX)
       platform/utf8-bytes
       (sign-hms256 (->  req :date platform.time/->aws-date))
       (sign-hms256 (->> req :endpoint :host (util.sign/host->region (req ::service))))
       (sign-hms256 (req ::service))
       (sign-hms256 MAGIC-SUFFIX)
       (sign-hms256 (signable-string req))
       platform/bytes->hex-str))

(defn- signed-authorization [req]
  (let [scope               (request-scope req)
        [headers canon-req] (canonical-request req)]
    (str
     ALGORITHM " "
     "Credential="    (-> req :creds :access-key (str "/" scope)) ", "
     "SignedHeaders=" (str/join ";" headers) ", "
     "Signature="     (sign (assoc req ::canonical canon-req ::scope scope)))))

(defn- required-headers [{:keys [date endpoint creds]}]
  (assoc-when
   {:x-amz-date (platform.time/->aws-date-time date)
    :host       (util.sign/host-header endpoint)}
   :x-amz-security-token (creds :token)))

(defn aws4
  [{:keys [endpoint body date ::service] :as req}]
  (let [date  (or (some-> date platform.time/from-long)
                  (signature-date (req :time-offset 0)))
        req   (-> req
                  (update :creds util.sign/sanitize-creds)
                  (assoc  :date  date))
        req   (update req :headers merge (required-headers req))
        hash  (platform.crypto/str->sha256 body)
        auth  (signed-authorization (assoc req ::hash hash))]
    (update req :headers merge {:authorization        auth
                                :x-amz-content-sha256 hash})))
