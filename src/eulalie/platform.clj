(ns eulalie.platform
  (:require [base64-clj.core :as base64]
            [cheshire.core   :as json])
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
