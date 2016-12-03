(ns eulalie.impl.sign
  (:require [eulalie.impl.util :as util]
            [clojure.string :as str])
  #?(:clj
     (:import [java.util.regex Pattern])))

(defn quote-region-regex [service-hint]
  (let [pattern (str "^(?:.+\\.)?"
                     #?(:clj
                        (Pattern/quote service-hint)
                        :cljs
                        (str/replace service-hint #"[-\\^$*+?.()|\[\]{}]" "\\$&"))
                     "[.-]([a-z0-9-]+)\\.")]
    #? (:clj
        (Pattern/compile pattern)
        :cljs
        (js/RegExp pattern))))

(defn host->region [service-hint host]
  ;; AWS does some CloundFront specific junk with service hints
  (some-> service-hint
          quote-region-regex
          (re-find host)
          last))

(defn sanitize-creds [m]
  (into {}
    (for [[k v] m]
      [k (cond-> v (string? v) str/trim)])))

(defn newline-join [& rest]
  (str/join "\n" rest))

(defn add-header [r k v]
  (assoc-in r [:headers k] v))

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
  (parse-region-name service-name host))

(def collapse-whitespace
  #(some-> % str (str/replace #"\s+" " ")))

(defn canonical-header [[k v]]
  [(-> k name str/lower-case collapse-whitespace)
   (collapse-whitespace v)])
