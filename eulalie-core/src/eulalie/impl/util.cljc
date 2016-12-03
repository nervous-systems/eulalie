(ns eulalie.impl.util
  (:require [clojure.string :as str]
            [eulalie.impl.platform.crypto :as platform.crypto]))

(defn- not-neg [x]
  (when (< -1 x)
    x))

(defn assoc-when [m & kvs]
  (reduce
   (fn [acc [k v]]
     (cond-> acc v (assoc k v)))
   m (partition 2 kvs)))

(defn update-when [m & kfs]
  (reduce
   (fn [acc [k f]]
     (cond-> acc (m k) (assoc k (f (m k)))))
   m (partition 2 kfs)))

(defn join-each [x pairs]
  (for [[k v] pairs]
    (str k x v)))

(defn to-first-match [^String hay ^String needle]
  (or (some->> (str/index-of hay needle) not-neg (subs hay 0)) hay))

(defn from-first-match [^String hay ^String needle]
  (or (some->> (str/index-of hay needle) not-neg inc (subs hay)) hay))

(defn from-last-match [^String hay ^String needle]
  (or (some->> (str/last-index-of hay needle) not-neg inc (subs hay)) hay))

(defn to-last-match [^String hay ^String needle]
  (or (some->> (str/last-index-of hay needle) not-neg (subs hay 0)) hay))

(defn response-checksum-ok? [{:keys [headers body]}]
  (let [crc (some-> headers :x-amz-crc32 #? (:clj Long/parseLong :cljs js/parseInt))]
    (or (not crc)
        (= (:content-encoding headers) "gzip")
        (= crc (platform.crypto/str->crc32 body)))))
