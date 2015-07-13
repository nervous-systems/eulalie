(ns eulalie.platform.crypto
  (:require [cljs.nodejs :as nodejs]))

(def crypto (nodejs/require "crypto"))

(defn str->sha256 [s]
  (let [hash (.createHash crypto "sha256")]
    (.update hash (js/Buffer. s "utf8"))
    (.digest hash "hex")))

(defn hmac-sha256 [bytes k]
  (let [mac (.createHmac crypto "sha256" k)]
    (.update mac (js/Buffer. bytes "utf8"))
    (.digest mac)))

(defn bytes->hex-str [bytes]
  (.toString (js/Buffer. bytes "utf8") "hex"))
