(ns eulalie.sign
  (:require [eulalie.util :as util]
            [eulalie.util.sign :as util.sign]
            [eulalie.platform.time :as platform.time]
            [eulalie.platform :as platform]
            [eulalie.platform.crypto :as platform.crypto]
            [eulalie.creds]
            [clojure.string :as string]
            [cemerick.url :as url]
            [clojure.set :refer [rename-keys]]))

(def KEY-PREFIX "AWS4")
(def MAGIC-SUFFIX "aws4_request")
(def ALGORITHM "AWS4-HMAC-SHA256")

(defn signature-date [offset-msecs]
  ;; Do this ourselves, because time/millis wants an int, and the
  ;; offset could be large
  (platform.time/from-long (- (platform.time/msecs-now) offset-msecs)))

(defn get-scope [service-name {:keys [host]} date]
  (let [region-name (util.sign/get-region service-name host)
        date-stamp (platform.time/->aws-date date)]
    (util.sign/slash-join date-stamp region-name service-name MAGIC-SUFFIX)))

(defn canonical-request
  [{:keys [method endpoint query-payload? headers]} hash]
  (let [{:keys [path query]} endpoint
        query (when-not query-payload?
                (some-> query url/map->query))
        [canon-headers canon-headers-s] (util.sign/canonical-headers headers)]
    [canon-headers
     (util.sign/newline-join
      (-> method name string/upper-case)
      (if (empty? path) "/" path)
      query
      canon-headers-s
      nil
      (string/join ";" canon-headers)
      hash)]))

(defn signable-string [date scope canon-req]
  (util.sign/newline-join
   ALGORITHM
   (platform.time/->aws-date-time date)
   scope
   (platform.crypto/str->sha256 canon-req)))

(defn sign-hms256 [s k]
  (platform.crypto/hmac-sha256
   (platform/get-utf8-bytes s)
   k))

(defn compute-signature
  [service-name creds {:keys [endpoint] :as r} date scope hash]
  (let [[headers canon-req] (canonical-request r hash)
        signature
        (->> (str KEY-PREFIX (:secret-key creds))
             platform/get-utf8-bytes
             (sign-hms256 (platform.time/->aws-date date))
             (sign-hms256 (util.sign/get-region service-name (:host endpoint)))
             (sign-hms256 service-name)
             (sign-hms256 MAGIC-SUFFIX)
             (sign-hms256 (signable-string date scope canon-req))
             platform.crypto/bytes->hex-str)]
    [headers signature]))

(defn required-headers [{{:keys [token]} :creds :keys [date endpoint]}]
  (cond->
      {:x-amz-date (platform.time/->aws-date-time date)
       :host (util.sign/host-header endpoint)}
    token (assoc :x-amz-security-token token)))

(defn aws4-sign
  [service-name
   {:keys [time-offset endpoint body date creds] :as r}]

  (let [{:keys [access-key token] :as creds}
        (-> creds eulalie.creds/creds->credentials util.sign/sanitize-creds)
        { date :date :as r}
        (-> r
            (assoc :creds creds)
            (assoc :date (or (some-> date platform.time/from-long)
                             (signature-date (or time-offset 0)))))
        r   (util.sign/add-headers r (required-headers r))

        hash  (platform.crypto/str->sha256 body)
        scope (get-scope service-name endpoint date)
        [header-names signature]  (compute-signature service-name creds r date scope hash)
        auth-params {"Credential" (util.sign/slash-join access-key scope)
                     "SignedHeaders" (string/join ";" header-names)
                     "Signature" signature}]
    (util.sign/add-headers
     r
     {:authorization (util.sign/make-header-value ALGORITHM auth-params)
      :x-amz-content-sha256 hash})))
