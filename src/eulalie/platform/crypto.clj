(ns eulalie.platform.crypto
  (:require [digest])
  (:import [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]
           [javax.xml.bind DatatypeConverter]))

(def str->sha256 digest/sha-256)

(def HMAC-SHA256 "HmacSHA256")

(defn hmac-sha256 [bytes k]
  (.doFinal
   (doto (Mac/getInstance HMAC-SHA256)
     (.init (SecretKeySpec. k HMAC-SHA256)))
   bytes))

(defn bytes->hex-str [bytes]
  (.toLowerCase (DatatypeConverter/printHexBinary bytes)))
