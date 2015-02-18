(ns eulalie.service-util
  (:require [eulalie.util :refer :all]
            [clojure.tools.logging :as log]
            [clojure.core.async :as async]
            [clojure.string :as string]
            [camel-snake-kebab.core :refer [->CamelCaseKeyword ->CamelCaseString ->kebab-case-keyword]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [clj-time.format]
            [clj-time.coerce]
            [clj-time.core :as clj-time])
  (:import java.nio.charset.Charset
           java.util.zip.CRC32))

(defn concretize-port [{:keys [protocol port] :as u}]
  (if-not (= port -1)
    u
    (assoc-in u [:port]
              (condp = protocol
                "http" 80
                "https" 443))))

(def utf-8 (Charset/forName "UTF-8"))

(defn get-utf8-bytes ^bytes [^String s]
  (.getBytes s ^Charset utf-8))

;; clj-time's :rfc882 formatter uses Z, whereas RFC 882 specifies the
;; equivalent of either Z or z.  AWS uses z.
(let [fmt (clj-time.format/formatter "EEE, dd MMM yyyy HH:mm:ss z")]
  (def rfc822->time (partial clj-time.format/parse fmt))
  (def time->rfc822 (partial clj-time.format/unparse fmt)))

(defn header [headers header]
  (let [value (headers header)]
    (cond-> value
      (coll? value) first)))

(defn parse-clock-skew [{:keys [headers]}]
  ;; TODO parse from SQS error message
  (or (some->> (header headers :date)
               rfc822->time
               clj-time.coerce/to-long
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

(defn response-checksum-ok? [{:keys [headers body]}]
  (let [crc (some-> headers :x-amz-crc32 Long/parseLong)]
    (or (not crc)
        (= crc (.getValue
                (doto (CRC32.)
                  (.update (get-utf8-bytes body))))))))

(def aws-date-format      (clj-time.format/formatters :basic-date))
(def aws-date-time-format (clj-time.format/formatters :basic-date-time-no-ms))

(def ->camel-k ->CamelCaseKeyword)
(def ->camel-s ->CamelCaseString)

(defn ->camel [x]
  (if (string? x)
    (->camel-s x)
    (->camel-k x)))

(def ->camel-keys-k (partial transform-keys ->CamelCaseKeyword))
(def ->camel-keys-s (partial transform-keys ->CamelCaseString))
