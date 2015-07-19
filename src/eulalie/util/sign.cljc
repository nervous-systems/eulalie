(ns eulalie.util.sign
  (:require #? (:cljs [cljs.nodejs :as nodejs])
            [eulalie.util :as util]
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

(def trim #(some-> % str/trim))

(defn sanitize-creds [m]
  (into {}
    (for [[k v] m]
      [k (cond-> v (string? v) str/trim)])))

(defn slash-join [& rest]
  (str/join "/" rest))

(defn newline-join [& rest]
  (str/join "\n" rest))

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
  #(some-> % str (str/replace #"\s+" " ")))

(def strip-down-header
  #(some-> % str/lower-case collapse-whitespace))

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
          (map #(str/join ":" %))
          (apply newline-join))]))

(defn make-header-value [v params]
  (str v (some->>
          params
          (map #(str/join "=" %))
          (str/join ", ")
          not-empty
          (str " "))))
