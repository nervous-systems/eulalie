(ns eulalie.platform.crypto
  (:require [goog.crypt :as crypt])
  (:import [goog.crypt Sha256 Hmac]))

(defn squish
  ([x] (bit-or 0x80 (bit-and x 0x3F)))
  ([x bit] (squish (bit-shift-right x bit))))

(defn str->utf8-bytes [xs & [i]]
  (when (< i (count xs))
    (lazy-seq
     (let [x (.charCodeAt xs i)]
       (cond
         (< x 0x80)  (cons x (str->utf8-bytes xs (inc i)))

         (< x 0x800) (cons (bit-or 0xC0 (bit-shift-right x 6))
                           (cons (squish x)
                                 (str->utf8-bytes xs (inc i))))

         (or (< x 0xd800) (<= 0xe000 x))
         (cons (bit-or 0xE0 (bit-shift-right x 12))
               (cons (squish x 6)
                     (cons (squish x)
                           (str->utf8-bytes xs (inc i)))))

         :else
         (let [y (.charCodeAt xs (+ i 2))
               x (+ 0x10000 (bit-or (bit-shift-left (bit-and x 0x3FF) 10)
                                    (bit-and y 0x3FF)))]
           (cons (bit-or 0xF0 (bit-shift-right x 18))
                 (cons (squish x 12)
                       (cons (squish x 6)
                             (cons (squish x)
                                   (str->utf8-bytes xs (+ i 2))))))))))))

(def str->utf8-array
  (js*
   "
function(str) {
  var out = [], p = 0;
  for (var i = 0; i < str.length; i++) {
    var c = str.charCodeAt(i);
    if (c < 128) {
      out[p++] = c;
    } else if (c < 2048) {
      out[p++] = (c >> 6) | 192;
      out[p++] = (c & 63) | 128;
    } else if (((c & 0xFC00) == 0xD800) &&
               (i + 1) < str.length &&
               ((str.charCodeAt(i + 1) & 0xFC00) == 0xDC00)) {
      // Surrogate Pair
      c = 0x10000 + ((c & 0x03FF) << 10) + (str.charCodeAt(++i) & 0x03FF);
      out[p++] = (c >> 18) | 240;
      out[p++] = ((c >> 12) & 63) | 128;
      out[p++] = ((c >> 6) & 63) | 128;
      out[p++] = (c & 63) | 128;
    } else {
      out[p++] = (c >> 12) | 224;
      out[p++] = ((c >> 6) & 63) | 128;
      out[p++] = (c & 63) | 128;
    }
  }
  return out;
}"))

(defn str->sha256 [s]
  (-> (doto (Sha256.) (.update s))
      .digest
      crypt/byteArrayToHex))

(defn hmac-sha256 [bytes k]
  (-> (Hmac. (Sha256.) (into-array (str->utf8-bytes k)))
      (.getHmac (if (string? bytes)
                  (into-array (str->utf8-bytes bytes))
                  bytes))))

(defn bytes->hex-str [bytes]
  (crypt/byteArrayToHex bytes))
