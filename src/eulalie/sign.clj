(ns eulalie.sign
  (:require [eulalie.util :refer :all]
            [eulalie.sign-util :refer :all]
            [clojure.string :as string]
            [cemerick.url    :as url]
            [clojure.set     :refer [rename-keys]]
            [clojure.tools.logging :as log]
            [digest])
  (:import [java.util Date]
           [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]
           [com.amazonaws AmazonClientException]
           [com.amazonaws.auth SigningAlgorithm]
           [com.amazonaws.util BinaryUtils]))

(def date-formatter (make-date-formatter "yyyyMMdd"))
(def time-formatter (make-date-formatter "yyyyMMdd'T'HHmmss'Z'"))

(def KEY-PREFIX "AWS4")
(def MAGIC-SUFFIX "aws4_request")
(def ALGORITHM "AWS4-HMAC-SHA256")

(defn signature-date [offset-secs]
  (-> (System/currentTimeMillis)
      ^Long (- (* offset-secs 1000))
      Date.
      .getTime))

(defn get-scope [{:keys [host]} date overrides]
  (let [region-name (get-or-calc-region host overrides)
        date-stamp (date-formatter date)
        {:keys [region-name service-name]} overrides]
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
   ALGORITHM (time-formatter date) scope (digest/sha-256 canon-req)))

(defn sign [s key alg]
  (try
    (.doFinal
     (doto (Mac/getInstance alg)
       (.init (SecretKeySpec. key alg)))
     s)
    (catch Exception e
      (throw
       (AmazonClientException.
        (str "Unable to calculate a request signature " (.getMessage e)) e)))))

(defn compute-signature
  [{:keys [endpoint] :as r} date scope hash creds overrides]
  (let [[headers canon-req] (canonical-request r hash)
        alg       (.toString SigningAlgorithm/HmacSHA256)
        sign*     (fn [^String s k] (sign (.getBytes s) k alg))
        signature (->> (str KEY-PREFIX (:secret-key creds))
                       .getBytes
                       (sign* (date-formatter date))
                       (sign* (get-or-calc-region (:host endpoint) overrides))
                       (sign* (:service-name overrides))
                       (sign* MAGIC-SUFFIX)
                       (sign* (signable-string date scope canon-req)))]
    (log/debug canon-req)
    
    [headers (BinaryUtils/toHex signature)]))

(defn aws4-sign [{:keys [time-offset endpoint body date] :as r} creds overrides]
  (let [{:keys [token access-key] :as creds} (sanitize-creds creds)
        date    (or date (signature-date (or time-offset 0)))
        hash    (digest/sha-256 body)
        headers (merge
                 {:x-amz-date (time-formatter date)
                  :host (host-header endpoint)}
                 (when token
                   {:x-amz-security-token token}))
        r       (-> r
                    (add-headers headers))
        scope (get-scope endpoint date overrides)
        [header-names signature]  (compute-signature r date scope hash creds overrides)
        auth-params {"Credential" (slash-join access-key scope)
                     "SignedHeaders" (string/join ";" header-names)
                     "Signature" signature}]
    (add-headers
     r
     {:authorization (make-header-value ALGORITHM auth-params)
      :x-amz-content-sha256 hash})))
