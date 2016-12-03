(ns eulalie.impl.platform
  (:require [goog.crypt :as crypt]))

(def utf8-bytes
  (js*
   "
function(str) {
  if (typeof str !== 'string') {
    return str;
  }
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

(def byte-count (comp count utf8-bytes))

(def bytes->hex-str crypt/byteArrayToHex)

(defn env [s & [default]]
  (or (aget js/process "env" (name s)) default))
