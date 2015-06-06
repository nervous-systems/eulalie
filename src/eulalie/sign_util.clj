(ns eulalie.sign-util
  (:require [eulalie.util :refer :all]
            [clojure.string :as string]
            [clojure.tools.logging :as log])
  (:import [org.joda.time.format
            DateTimeFormatter
            DateTimeFormat]
           [java.util.regex Pattern]))

(def trim (fn-some-> string/trim))

(defn sanitize-creds [m]
  (into {}
    (for [[k v] m]
      [k (string/trim v)])))

(defn slash-join [& rest]
  (string/join "/" rest))

(defn newline-join [& rest]
  (string/join "\n" rest))

(defn add-header [r k v]
  (assoc-in r [:headers k] v))

(defn add-headers [r m]
  (update-in r [:headers] merge m))

(defn default-port? [{:keys [protocol port]}]
  (or (and (= protocol "http")  (= port 80))
      (and (= protocol "https") (= port 443))))

(defn host-header [{:keys [host port] :as endpoint}]
  (if (default-port? endpoint)
    host
    (str host ":" port)))

(def default-region "us-east-1")

(defn parse-standard-region-name [fragment]
  ;; AWS has a bunch of S3 & cloudfront special-casing here
  (let [region (from-last-match fragment ".")]
    ;; no dot
    (cond (= region fragment) default-region
          (= region "us-gov") "us-gov-west-1"
          :else               region)))

(defn host->region [service-hint host]
  (when service-hint
    ;; AWS does some CloundFront specific junk with service hints
    (let [pattern (re-matcher
                   (re-pattern
                    (str "^(?:.+\\.)?"
                         (Pattern/quote service-hint)
                         "[.-]([a-z0-9-]+)\\."))
                   host)]
      (-> pattern re-find last))))

(defn parse-region-name [service-hint host]
  (let [prefix (to-last-match host ".amazonaws.com")]
    (if (not= prefix host)
      (parse-standard-region-name prefix)
      (or (host->region service-hint host) default-region))))

(defn get-or-calc-region [service-name host]
  ;; TODO maybe allow region to be overridden?  it's not clear under
  ;; what circumstances we won't be able to figure it out.
  (parse-region-name service-name host))

(def collapse-whitespace
  (fn-some-> (string/replace #"\s+" " ")))

(def strip-down-header
  (fn-some-> string/lower-case collapse-whitespace))

(defn canonical-header [[k v]]
  [(strip-down-header
    (if (keyword? k)
      (name k)
      k))
   (collapse-whitespace v)])

(defn canonical-headers [m]
  (let [m (->> m
               (map canonical-header)
               (into (sorted-map)))]
    [(keys m)
     (->> m
          (map (fn->> (string/join ":")))
          (apply newline-join))]))

(defn make-header-value [v params]
  (str v (some->>
          params
          (map (fn->> (string/join "=")))
          (string/join ", ")
          not-empty
          (str " "))))
