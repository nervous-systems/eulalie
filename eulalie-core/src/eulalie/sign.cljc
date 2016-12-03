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

(defn- get-scope [service {:keys [host]} date]
  (let [region-name (util.sign/get-region service host)
        date-stamp  (platform.time/->aws-date date)]
    (str/join "/" [date-stamp region-name service MAGIC-SUFFIX])))

(defn- canonical-request
  [{:keys [method endpoint query-payload? headers] :as req} hash]
  (let [query   (when-not query-payload?
                  (some-> req :query url/map->query))
        headers (into (sorted-map) (map util.sign/canonical-header) headers)]
    [(keys headers)
     (util.sign/newline-join
      (-> method name str/upper-case)
      (-> (:path endpoint) not-empty (or "/"))
      (:query endpoint)
      (str/join "\n" (util/join-each ":" headers))
      nil
      (str/join ";" (keys headers))
      hash)]))

(defn- signable-string [date scope canon-req]
  (util.sign/newline-join
   ALGORITHM
   (platform.time/->aws-date-time date)
   scope
   (platform.crypto/str->sha256 canon-req)))

(defn- sign-hms256 [s k]
  (platform.crypto/hmac-sha256
   (platform/utf8-bytes s)
   k))

(defn- sign [{:keys [req scope canon-req] :as args}]
  (->> req :creds :secret-key (str KEY-PREFIX)
       platform/utf8-bytes
       (sign-hms256 (->  req :date platform.time/->aws-date))
       (sign-hms256 (->> req :endpoint :host (util.sign/get-region (args :service))))
       (sign-hms256 (args :service))
       (sign-hms256 MAGIC-SUFFIX)
       (sign-hms256 (signable-string (req :date) scope canon-req))
       platform/bytes->hex-str))

(defn- signed-authorization
  [{:keys [service req] :as args}]
  (let [scope               (get-scope service (req :endpoint) (req :date))
        [headers canon-req] (canonical-request req (args :hash))]
    (str
     ALGORITHM " "
     "Credential="    (-> req :creds :access-key (str "/" scope)) ", "
     "SignedHeaders=" (str/join ";" headers) ", "
     "Signature="     (sign (assoc args :canon-req canon-req :scope scope)))))

(defn- required-headers [{:keys [date endpoint creds]}]
  (assoc-when
   {:x-amz-date (platform.time/->aws-date-time date)
    :host       (util.sign/host-header endpoint)}
   :x-amz-security-token (creds :token)))

(defn aws4
  [service {:keys [endpoint body date] :as req}]
  (let [date  (or (some-> date platform.time/from-long)
                  (signature-date (req :time-offset 0)))
        req   (-> req
                  (update :creds util.sign/sanitize-creds)
                  (assoc  :date  date))
        req   (update req :headers merge (required-headers req))
        hash  (platform.crypto/str->sha256 body)
        auth  (signed-authorization {:service service :hash hash :req req})]
    (update req :headers merge {:authorization        auth
                                :x-amz-content-sha256 hash})))
