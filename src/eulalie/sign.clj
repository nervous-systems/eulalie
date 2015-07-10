(ns eulalie.sign
  (:require [eulalie.util :refer :all]
            [eulalie.sign-util :refer :all]
            [eulalie.service-util :refer [aws-date-format aws-date-time-format]]
            [eulalie.creds]
            [clojure.string :as string]
            [cemerick.url    :as url]
            [clojure.set     :refer [rename-keys]]
            [clj-time.core :as time]
            [clj-time.format :as time-format]
            [clj-time.coerce :as time-coerce]
            [digest])
  (:import [java.util Date]
           [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]
           [javax.xml.bind DatatypeConverter]))

(def KEY-PREFIX "AWS4")
(def MAGIC-SUFFIX "aws4_request")
(def ALGORITHM "AWS4-HMAC-SHA256")

(defn signature-date [offset-msecs]
  ;; Do this ourselves, because time/millis wants an int, and the
  ;; offset could be large
  (time-coerce/from-long (- (time-coerce/to-long (time/now)) offset-msecs)))

(defn get-scope [service-name {:keys [host]} date]
  (let [region-name (get-or-calc-region service-name host)
        date-stamp (time-format/unparse aws-date-format date)]
    (slash-join date-stamp region-name service-name MAGIC-SUFFIX)))

(defn canonical-request
  [{:keys [method endpoint query-payload? headers]} hash]
  (let [{:keys [path query]} endpoint
        query (when-not query-payload?
                (some-> query url/map->query))
        [canon-headers canon-headers-s] (canonical-headers headers)]
    [canon-headers
     (newline-join
      (-> method name string/upper-case)
      (if (empty? path) "/" path)
      query
      canon-headers-s
      nil
      (string/join ";" canon-headers)
      hash)]))

(defn signable-string [date scope canon-req]
  (newline-join
   ALGORITHM
   (time-format/unparse aws-date-time-format date)
   scope
   (digest/sha-256 canon-req)))

(defn sign [s key alg]
  (.doFinal
   (doto (Mac/getInstance alg)
     (.init (SecretKeySpec. key alg)))
   s))

(def HMAC-SHA256 "HmacSHA256")

(defn compute-signature
  [service-name creds {:keys [endpoint] :as r} date scope hash]
  (let [[headers canon-req] (canonical-request r hash)
        sign*     (fn [^String s k] (sign (get-utf8-bytes s) k HMAC-SHA256))
        signature (->> (str KEY-PREFIX (:secret-key creds))
                       get-utf8-bytes
                       (sign* (time-format/unparse aws-date-format date))
                       (sign* (get-or-calc-region service-name (:host endpoint)))
                       (sign* service-name)
                       (sign* MAGIC-SUFFIX)
                       (sign* (signable-string date scope canon-req)))]
    [headers (.toLowerCase (DatatypeConverter/printHexBinary signature))]))

(defn required-headers [{{:keys [token]} :creds :keys [date endpoint]}]
  (cond->
      {:x-amz-date (time-format/unparse aws-date-time-format date)
       :host (host-header endpoint)}
    token (assoc :x-amz-security-token token)))

(defn aws4-sign
  [service-name
   {:keys [time-offset endpoint body date creds] :as r}]

  (let [{:keys [access-key token] :as creds}
        (-> creds eulalie.creds/creds->credentials sanitize-creds)
        { date :date :as r}
        (-> r
            (assoc :creds creds)
            (assoc :date (or (some-> date time-coerce/from-long)
                             (signature-date (or time-offset 0)))))
        r   (add-headers r (required-headers r))

        hash  (digest/sha-256 body)
        scope (get-scope service-name endpoint date)
        [header-names signature]  (compute-signature service-name creds r date scope hash)
        auth-params {"Credential" (slash-join access-key scope)
                     "SignedHeaders" (string/join ";" header-names)
                     "Signature" signature}]
    (add-headers
     r
     {:authorization (make-header-value ALGORITHM auth-params)
      :x-amz-content-sha256 hash})))
