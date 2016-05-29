(ns eulalie.platform
  (:require [cljs.nodejs :as nodejs]))

(def buffer-crc32 (nodejs/require "buffer-crc32"))

(defn decode-json [s]
  (js->clj (.parse js/JSON s) :keywordize-keys true))

(def byte-count #(.byteLength js/Buffer % "utf8"))

(defn get-utf8-bytes [s]
  (js/Buffer. s "utf8"))

(defn response-checksum-ok? [{:keys [headers body]}]
  (let [input-crc (some-> headers :x-amz-crc32 js/Number)]
    (or (not input-crc)
        (= (:content-encoding headers) "gzip")
        (= input-crc (.unsigned buffer-crc32 (get-utf8-bytes body))))))

(defn encode-json [x]
  (.stringify js/JSON (clj->js x)))

(defn encode-base64 [x]
  (.toString (js/Buffer. x "utf8") "base64"))

(defn decode-base64 [x]
  (.toString (js/Buffer. x "base64") "utf8"))

(defn byte-array? [x]
  (instance? js/Buffer x))

(defn ba->b64-string [x]
  (.toString x "base64"))

(defn b64-string->ba [x]
  (js/Buffer. x "base64"))
