(ns eulalie.impl.service
  (:require [eulalie.impl.platform.time :as platform.time]
            [eulalie.impl.sign :refer [DEFAULT-REGION]]
            [eulalie.impl.platform.crypto :as platform.crypto]
            [eulalie.impl.util :as util]
            [cemerick.url :as url]
            [camel-snake-kebab.core :as csk :refer [->kebab-case-keyword]]
            [camel-snake-kebab.extras :refer [transform-keys]]))

(def throttling-error?
  #{:throttling
    :throttling-exception
    :provisioned-throughput-exceeded-exception})

(defn concretize-port [{:keys [protocol port] :as u}]
  (if-not (= port -1)
    u
    (assoc u :port
           (case protocol
             "http"  80
             "https" 443))))

(defn header [headers header]
  (let [value (headers header)]
    (cond-> value
      (coll? value) first)))

(defn parse-clock-skew [{:keys [headers]}]
  ;; TODO parse from SQS error message
  (or (some->> (header headers :date)
               platform.time/rfc822->time
               platform.time/to-long
               (- (platform.time/msecs-now)))
      0))

(def clock-skew-error?
  #{:request-time-too-skewed
    :request-expired
    :invalid-signature-exception
    :signature-does-not-match})

(defn retry? [status {:keys [transport type]}]
  (or transport
      (= status 500)
      (= status 503)
      (throttling-error? type)
      (clock-skew-error? type)))

(defn headers->error-type [m]
  (some->
   m
   (header :x-amzn-errortype)
   not-empty
   (util/to-first-match ":")
   not-empty
   ->kebab-case-keyword))

(defn decorate-error [{:keys [type] :as e} resp]
  (if (clock-skew-error? type)
    (assoc e :time-offset (parse-clock-skew resp))
    e))

(def ->camel-k csk/->PascalCaseKeyword)
(def ->kebab-k ->kebab-case-keyword)
(def ->camel-s csk/->PascalCaseString)

(defn ->camel [x]
  (if (string? x)
    (->camel-s x)
    (->camel-k x)))

(def ->camel-keys-k (partial transform-keys csk/->PascalCaseKeyword))
(def ->camel-keys-s (partial transform-keys csk/->PascalCaseString))

(defn region->tld [region]
  (case (name region)
    "cn-north-1" ".com.cn"
    ".com"))

(defn region->endpoint [region {service :service-name}]
  (url/url (str "https://"
                (name service) "." (name region)
                ".amazonaws"
                (region->tld region))))

(defn default-request [service {:keys [creds] :as req}]
  (let [region   (some :region   [req creds service {:region DEFAULT-REGION}])
        endpoint (some :endpoint [req creds])]
    (merge {:max-retries (service :max-retries)
            :endpoint    (or (cond-> endpoint
                               (string? endpoint) url/url)
                             (region->endpoint region service))
            :region      region
            :method      :post} req)))

(defn response-checksum-ok? [{:keys [headers body]}]
  (let [crc (some-> headers :x-amz-crc32 #? (:clj Long/parseLong :cljs js/parseInt))]
    (or (not crc)
        (= (:content-encoding headers) "gzip")
        (= crc (platform.crypto/str->crc32 body)))))
