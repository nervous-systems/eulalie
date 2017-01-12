(ns ^:no-doc eulalie.platform
  #?(:clj  (:require [base64-clj.core :as base64]
                     [cheshire.core   :as json])
     :cljs (:require [eulalie.impl.platform :refer [utf8-bytes]]
                     [eulalie.impl.platform.util :as platform.util]
                     [goog.crypt.base64 :as base64]
                     [clojure.walk      :as walk]
                     [clojure.string    :as str])))

(defn encode-base64 [x]
  #? (:clj  (base64/encode x)
      :cljs (base64/encodeByteArray (utf8-bytes x))))

(defn decode-base64 [x]
  #? (:clj  (if (empty? x)
              x
              (base64/decode x))
      :cljs (platform.util/bytes->string
             (base64/decodeStringToByteArray x))))

#?(:cljs
   (defn- stringify-kv [[k v]]
     (if-not (keyword? k)
       [k v]
       (if-let [ns (namespace k)]
         [(str ns "/" (name k)) v]
         [(name k) v]))))

(defn encode-json
  "Encode with namespace aware (i.e. include namespace) key stringication."
  [x]
  #? (:clj (json/encode x)
      :cljs
      (let [stringed (clojure.walk/postwalk
                      (fn [form]
                        (if (map? form)
                          (into {} (map stringify-kv form))
                          form))
                      x)]
        (js/JSON.stringify (clj->js stringed)))))

#?(:cljs
   (defn- keywordize-kv [[k v]]
     (if (str/index-of k "/")
       [(apply keyword (str/split k #"/" 2)) v]
       [(keyword k) v])))

(defn decode-json "Decode w/ namespace-aware key keywordization"
  [x]
  #? (:clj  (json/decode x keyword)
      :cljs (clojure.walk/postwalk
             (fn [form]
               (if (map? form)
                 (into {} (map keywordize-kv form))
                 form))
             (js->clj (js/JSON.parse x)))))
