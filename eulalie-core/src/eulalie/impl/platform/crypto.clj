(ns eulalie.impl.platform.crypto
  (:require [digest]
            [eulalie.impl.platform :refer [utf8-bytes]])
  (:import [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]
           [java.util.zip CRC32]))

(def str->sha256 digest/sha-256)

(def HMAC-SHA256 "HmacSHA256")

(defn hmac-sha256 [bytes k]
  (.doFinal
   (doto (Mac/getInstance HMAC-SHA256)
     (.init (SecretKeySpec. k HMAC-SHA256)))
   bytes))

(defn str->crc32 [body]
  (.getValue
   (doto (CRC32.)
     (.update (utf8-bytes body)))))
