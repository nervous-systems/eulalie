(ns eulalie.sign
  (:require [eulalie.util :refer :all]
            [clojure.string :as string]
            [cemerick.url    :as url]
            [digest])
  (:import [org.joda.time.format
            DateTimeFormatter
            DateTimeFormat]))

(let [fmt (-> (DateTimeFormat/forPattern "yyyyMMdd") .withZoneUTC)]
  (def date-formatter #(.print fmt %)))

(let [fmt (-> (DateTimeFormat/forPattern "yyyyMMdd'T'HHmmss'Z'")
              .withZoneUTC)]
  (def time-formatter #(.print fmt %)))

(let [trim (fn-some-> string/trim)]
  (def sanitize-creds
    (map-rewriter
     {:access-key trim
      :secret-key trim
      :token      trim})))

(defn add-header [r k v]
  (update-in r [:headers] (fn-> (assoc k v))))

(defn add-headers [r m]
  (update-in r [:headers] (fn-> (merge m))))

(defn default-port? [{:keys [protocol port]}]
  (or (and (= protocol "http")  (= port 80))
      (and (= protocol "https") (= port 443))))

(defn host-header [{{:keys [host port] :as endpoint} :endpoint}]
  (if (default-port? endpoint)
    host
    (str host ":" port)))

(defn signature-date [offset-secs]
  (-> (System/currentTimeMillis.)
      (- (* offset-secs 1000))
      Date.
      .getTime))

(def value-separator "/")

(defn join-values [& rest]
  (string/join value-separator rest))

(defn get-or-calc-region [host {:keys [region-name service-name]}]
  (or region-name
      (AwsHostNameUtils/parseRegionName
       ^String host ^String service-name)))

(def terminator "aws4_request")

(defn get-scope [{:keys [host]} date overrides]
  (let [region-name (get-or-calc-region host overrides)
        date-stamp (date-formatter date)
        {:keys [region-name service-name]} overrides]
    (join-values date-stamp region-name service-name terminator)))

(def strip-down-header
  (fn-some-> string/lower-case (string/replace #"\s+" " ")))

(defn canonical-header [[k v]]
  (let [h [(strip-down-header k) ":"]]
    (if v
      (conj h (strip-down-header v))
      v)))

(def canonical-headers
  (fn->>
   (into (sorted-map))
   (map (fn->> canonical-header (apply str)))
   (string/join "\n")))

(def header-names
  (fn->>
   (into (sorted-map))
   keys
   (map strip-down-header)
   (string/join ";")))

(defn canonical-request
  [{:keys [method endpoint query-payload? headers]} hash]
  (let [{:keys [path query]} endpoint
        query (when-not query-payload?
                (some-> query url/map->query))]
    (string/join
     "\n"
     [(-> method name string/upper-case)
      path
      query
      (canonical-headers headers) nil
      (header-names headers)
      hash])))

(defn signable-string [date alg scope canon-req]
  (string/join
   "\n"
   [alg (time-formatter date) scope (digest/sha-256 canon-req)]))

(defn compute-signature
  [{{:keys [host]} :endpoint :as r} date alg scope hash creds overrides]
  (let [region-name (get-or-calc-region host overrides)
        {:keys [service-name]} overrides
        canon-req (canonical-request r hash)
        signable  (signable-string date alg scope canon-req)
        k-signing (->> (str "AWS4" (:secret-key creds))
                       (sign (date-formatter date))
                       (sign region-name)
                       (sign service-name)
                       (sign terminator))]
    {:date  date
     :scope scope
     :key   k-signing
     :signature (sign signable k-signing)}))

(def algorithm "AWS4-HMAC-SHA256")

(defn make-header-value [v params]
  (str v (some->>
          params
          (map (fn->> (string/join "=")))
          (string/join ", ")
          not-empty
          (str " "))))

(defn aws4-sign [{:keys [time-offset endpoint body] :as r} creds overrides]
  (let [{:keys [token access-key] :as creds} (sanitize-creds creds)
        date  (signature-date time-offset)
        hash  (digest-sha256 body)
        r     (add-headers
               (merge
                {:x-amz-date (time-formatter date)
                 :x-amz-content-sha256 hash
                 :host (host-header r)}
                (when token
                  {:x-amz-security-token token})))
        scope (get-scope endpoint date overrides)

        {:keys [signature]}
        (compute-signature r date algorithm scope hash creds overrides)
        auth (make-header-value
              algorithm
              {"Credential" (join-values access-key scope)
               "SignedHeaders" (-> r :headers header-names)
               "Signature" signature})]
    (add-header r :authorization auth)))
