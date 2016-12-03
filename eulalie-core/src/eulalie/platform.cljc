(ns eulalie.platform
  #?(:clj  (:require [base64-clj.core :as base64]
                     [cheshire.core   :as json])
     :cljs (:require [eulalie.impl.platform :refer [utf8-bytes]]
                     [eulalie.impl.platform.util :as platform.util]
                     [goog.crypt.base64 :as base64])))

(defn encode-base64 [x]
  #? (:clj  (base64/encode x)
      :cljs (base64/encodeByteArray (utf8-bytes x))))

(defn decode-base64 [x]
  #? (:clj  (base64/decode x)
      :cljs (platform.util/bytes->string
             (base64/decodeStringToByteArray x))))

(defn encode-json [x]
  #? (:clj  (json/encode x)
      :cljs (js/JSON.stringify (clj->js x))))

(defn decode-json [x]
  #? (:clj  (json/decode x keyword)
      :cljs (js->clj (js/JSON.parse x) :keywordize-keys true)))
