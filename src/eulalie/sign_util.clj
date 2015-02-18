(ns eulalie.sign-util
  (:require [eulalie.util :refer :all]
            [clojure.string :as string]
            [clojure.tools.logging :as log])
  (:import [org.joda.time.format
            DateTimeFormatter
            DateTimeFormat]
           [com.amazonaws.util AwsHostNameUtils]))

(def trim (fn-some-> string/trim))

(def sanitize-creds
  (map-rewriter
   {:access-key trim
    :secret-key trim
    :token      trim}))

(defn slash-join [& rest]
  (string/join "/" rest))

(defn newline-join [& rest]
  (string/join "\n" rest))

(defn add-header [r k v]
  (update-in r [:headers] (fn-> (assoc k v))))

(defn add-headers [r m]
  (update-in r [:headers] (fn-> (merge m))))

(defn default-port? [{:keys [protocol port]}]
  (or (and (= protocol "http")  (= port 80))
      (and (= protocol "https") (= port 443))))

(defn host-header [{:keys [host port] :as endpoint}]
  (if (default-port? endpoint)
    host
    (str host ":" port)))

(defn get-or-calc-region [service-name host]
  ;; TODO maybe allow region to be overridden?  it's not clear under
  ;; what circumstances we won't be able to figure it out.
  (AwsHostNameUtils/parseRegionName
   ^String host ^String service-name))

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
