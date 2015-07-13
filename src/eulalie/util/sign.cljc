(ns eulalie.util.sign
  (:require #? (:cljs [cljs.nodejs :as nodejs])
            [eulalie.util :as util]
            [clojure.string :as string])
  #?(:clj
     (:import [java.util.regex Pattern])))

#?(:cljs (nodejs/require "regexp-quote"))

(defn quote-region-regex [service-hint]
  (str "^(?:.+\\.)?"
       #?(:clj
          (Pattern/quote service-hint)
          :cljs
          (.quote js/RegExp service-hint))
       "[.-]([a-z0-9-]+)\\."))

(defn host->region [service-hint host]
  ;; AWS does some CloundFront specific junk with service hints
  (some-> service-hint
          quote-region-regex
          (re-find host)
          last))

(def trim #(some-> % string/trim))

(defn sanitize-creds [m]
  (into {}
    (for [[k v] m]
      [k (cond-> v (string? v) string/trim)])))

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
  (let [region (util/from-last-match fragment ".")]
    ;; no dot
    (cond (= region fragment) default-region
          (= region "us-gov") "us-gov-west-1"
          :else               region)))

(defn parse-region-name [service-hint host]
  (let [prefix (util/to-last-match host ".amazonaws.com")]
    (if (not= prefix host)
      (parse-standard-region-name prefix)
      (or (host->region service-hint host) default-region))))

(defn get-region [service-name host]
  ;; TODO maybe allow region to be overridden?  it's not clear under
  ;; what circumstances we won't be able to figure it out.
  (parse-region-name service-name host))

(def collapse-whitespace
  #(some-> % str (string/replace #"\s+" " ")))

(def strip-down-header
  #(some-> % string/lower-case collapse-whitespace))

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
          (map #(string/join ":" %))
          (apply newline-join))]))

(defn make-header-value [v params]
  (str v (some->>
          params
          (map #(string/join "=" %))
          (string/join ", ")
          not-empty
          (str " "))))
