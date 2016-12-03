(ns eulalie.impl.platform.crypto
  (:require [goog.crypt :as crypt]
            [eulalie.impl.platform :refer [utf8-bytes]]
            [eulalie.impl.platform.crypto.crc32 :as crc32])
  (:import [goog.crypt Sha256 Hmac]))

(defn str->sha256 [s]
  (-> (doto (Sha256.)
        (.update s))
      .digest
      crypt/byteArrayToHex))

(defn hmac-sha256 [bytes k]
  (-> (Hmac. (Sha256.) (utf8-bytes k))
      (.getHmac (if (string? bytes)
                  (utf8-bytes bytes)
                  bytes))))

(def str->crc32 crc32/str->crc32)
