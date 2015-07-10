(ns eulalie.util.service
  (:require
   [eulalie.util :refer :all]
   [clojure.core.async :as async]
   [clojure.string :as string]
   [clj-time.format :as time-format]
   [clj-time.coerce :as time-coerce]
   [cemerick.url :as url]
   [camel-snake-kebab.core :refer
    [->PascalCaseKeyword
     ->PascalCaseString
     ->kebab-case-keyword]]
   [camel-snake-kebab.extras :refer [transform-keys]]))

(defn concretize-port [{:keys [protocol port] :as u}]
  (if-not (= port -1)
    u
    (assoc u :port
           (case protocol
             "http" 80
             "https" 443))))

;; clj-time's :rfc882 formatter uses Z, whereas RFC 882 specifies the
;; equivalent of either Z or z.  AWS uses z.
(let [fmt (time-format/formatter "EEE, dd MMM yyyy HH:mm:ss z")]
  (def rfc822->time (partial time-format/parse fmt))
  (def time->rfc822 (partial time-format/unparse fmt)))

(defn header [headers header]
  (let [value (headers header)]
    (cond-> value
      (coll? value) first)))

(defn parse-clock-skew [{:keys [headers]}]
  ;; TODO parse from SQS error message
  (or (some->> (header headers :date)
               rfc822->time
               time-coerce/to-long
               (- (msecs-now)))
      0))

(def throttling-error?
  #{:throttling
    :throttling-exception
    :provisioned-throughput-exceeded-exception})

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

(def headers->error-type
  (fn-some->
   (header :x-amzn-errortype) not-empty (to-first-match ":") not-empty
   ->kebab-case-keyword))

(defn decorate-error [{:keys [type] :as e} resp]
  (if (clock-skew-error? type)
    (assoc e :time-offset (parse-clock-skew resp))
    e))

(defn http-kit->error [^Exception e]
  (when e
    {:message (.getMessage e)
     :type    (-> e
                  .getClass
                  .getSimpleName
                  (to-first-match "Exception")
                  ->kebab-case-keyword)
     :exception e
     :transport true}))

(let [scale-ms             300
      throttle-scale-ms    500
      throttle-scale-range (/ throttle-scale-ms 4)
      max-backoff-ms       (* 20 1000)]

  (defn default-retry-backoff [retries {:keys [type]}]
    (when-not (zero? retries)
      (let [scale-factor
            (if (throttling-error? type)
              (+ throttle-scale-ms (rand-int throttle-scale-range))
              scale-ms)]
        (-> 1
            (bit-shift-left retries)
            (* scale-factor)
            (min max-backoff-ms)
            async/timeout)))))

(def aws-date-format      (time-format/formatters :basic-date))
(def aws-date-time-format (time-format/formatters :basic-date-time-no-ms))

(def ->camel-k ->PascalCaseKeyword)
(def ->kebab-k ->kebab-case-keyword)
(def ->camel-s ->PascalCaseString)

(defn ->camel [x]
  (if (string? x)
    (->camel-s x)
    (->camel-k x)))

(def ->camel-keys-k (partial transform-keys ->PascalCaseKeyword))
(def ->camel-keys-s (partial transform-keys ->PascalCaseString))

(defn region->tld [region]
  (case (name region)
    "cn-north-1" ".com.cn"
    ".com"))

;; The signers want the region, we ought to make it available instead of having
;; them extract it from this endpoint
(defn region->endpoint [region {service :service-name}]
  ;; This is right for now
  (url/url (str "https://"
                (name service) "." (name region)
                ".amazonaws"
                (region->tld region))))

(defn default-request [{:keys [max-retries] :as service} {:keys [creds] :as req}]
  (let [region (some :region [req creds service])
        endpoint (some :endpoint [req creds])]
    (merge {:max-retries max-retries
            :endpoint (or (cond-> endpoint
                            (string? endpoint) url/url)
                          (region->endpoint region service))
            :method :post} req)))
