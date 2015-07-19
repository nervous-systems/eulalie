(ns eulalie.platform
  (:require [base64-clj.core :as base64]
            [camel-snake-kebab.core :as csk]
            [cheshire.core :as json]
            [clojure.core.async :as async]
            [clojure.set :as set]
            [clojure.walk :as walk]
            [eulalie.util :as util]
            [glossop.util :refer [close-with!]]
            [org.httpkit.client :as http])
  (:import [java.nio.charset Charset]
           [java.util.zip CRC32]))

(let [utf-8 (Charset/forName "UTF-8")]
  (defn get-utf8-bytes ^bytes [^String s]
    (.getBytes s ^Charset utf-8)))

(defn byte-count [s]
  (count (get-utf8-bytes s)))

(defn response-checksum-ok? [{:keys [headers body]}]
  (let [crc (some-> headers :x-amz-crc32 Long/parseLong)]
    (or (not crc)
        ;; We're not going to calculate the checksum of gzipped responses, since
        ;; we need access to the raw bytes - look into how to do this with
        ;; httpkit
        (= (:content-encoding headers) "gzip")
        (= crc (.getValue
                (doto (CRC32.)
                  (.update (get-utf8-bytes body))))))))

(defn http-response->error [^Exception e]
  (when e
    {:message (.getMessage e)
     :type    (-> e
                  .getClass
                  .getSimpleName
                  (util/to-first-match "Exception")
                  csk/->kebab-case-keyword)
     :exception e
     :transport true}))

(defn req->http-kit [{:keys [endpoint headers] :as m}]
  (-> m
      (dissoc :endpoint)
      (assoc :url (str endpoint)
             :headers (walk/stringify-keys headers))))

(defn channel-request! [m]
  (let [ch (async/chan)]
    (http/request m #(close-with! ch %) )
    ch))

(defn http-get! [url]
  (channel-request! {:url (str url)}))

(defn channel-aws-request! [m]
  (channel-request! (req->http-kit m)))

(def encode-json json/encode)
(def decode-json #(json/decode % true))
(def encode-base64 base64/encode)
(def decode-base64 base64/decode)

(defn- array-ctor->type-checker [t]
  (partial instance? (type (t []))))

(def byte-array? (array-ctor->type-checker byte-array))

(def utf-8 (Charset/forName "UTF-8"))

(defn ba->b64-string [^bytes x]
  (String. ^bytes (base64/encode-bytes x) ^Charset utf-8))

(defn b64-string->ba [^String x]
  (base64/decode-bytes (.getBytes x ^Charset utf-8)))
