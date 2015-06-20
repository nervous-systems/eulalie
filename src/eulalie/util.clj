(ns eulalie.util
  (:require [clojure.tools.logging :as log]
            [clojure.set :refer [rename-keys]]
            [clojure.core.async :as async]
            [clojure.string :as string]
            [clojure.algo.generic.functor :as functor]
            [clj-time.core :as clj-time]
            [clj-time.coerce]
            [org.httpkit.client :as http])
  (:import java.nio.charset.Charset))

(defmethod functor/fmap clojure.lang.Keyword [f v] (f v))
(defmethod functor/fmap :default [f v] (f v))

(defmacro go-catching [& body]
  `(async/go
     (try
       ~@body
       (catch Exception e#
         (log/error e# "Caught exception in (go) block")
         e#))))

(defn map-rewriter [spec]
  (fn [m]
    (reduce
     (fn [m [k v]]
       (cond
         (vector?  v) (update-in m [k] #(get-in % v))
         (keyword? v) (rename-keys m {k v})
         (fn?      v) (update-in m [k] v)
         :else        (assoc m k v)))
     m
     (if (map? spec)
       spec
       (partition 2 spec)))))

(defn rewrite-map [m spec]
  ((map-rewriter spec) m))

(defmacro mapmap [body & ls]
  `(into {} (map (fn [[~'K ~'V]] ~body) ~@ls)))

(defn invert-map [m]
  (mapmap [V K] m))

(defn mapkv [kf vf m]
  (into {} (map (fn [[k v]] [(kf k) (vf v)]) m)))

(defn mapkeys [f m]
  (mapkv f identity m))

(defn mapvals [f m]
  (mapkv identity f m))

(defmacro fn->       [& forms] `(fn [x#] (-> x# ~@forms)))
(defmacro fn->>      [& forms] `(fn [x#] (->> x# ~@forms)))
(defmacro fn-some->  [& forms] `(fn [x#] (some-> x# ~@forms)))
(defmacro fn-some->> [& forms] `(fn [x#] (some->> x# ~@forms)))

(defn throw-err [e]
  (when (instance? Throwable e)
    (throw e))
  e)

(defmacro <? [ch]
  `(throw-err (async/<! ~ch)))

(defn <?! [ch]
  (throw-err (async/<!! ch)))

(defn- when-not-pred-fn [p]
  #(when-not (p %)
     %))

(def not-zero (when-not-pred-fn zero?))
(def not-neg  (when-not-pred-fn neg?))

(defn to-first-match [^String hay ^String needle]
  (or (some->> (.indexOf hay needle) not-neg (subs hay 0)) hay))

(defn from-first-match [^String hay ^String needle]
  (or (some->> (.indexOf hay needle) not-neg inc (subs hay)) hay))

(defn from-last-match [^String hay ^String needle]
  (or (some->> (.lastIndexOf hay needle) not-neg inc (subs hay)) hay))

(defn to-last-match [^String hay ^String needle]
  (or (some->> (.lastIndexOf hay needle) not-neg (subs hay 0)) hay))

(defn last-char [s]
  (->> s count dec (get s)))

(defn cointoss? []
  (zero? (rand-int 2)))

(defn get-unqualified-name [^Class c]
  (.getSimpleName c))

;; Taken from encore
(defn fq-name "Like `name` but includes namespace in string when present."
  [x]
  (if (string? x) x
      (let [n (name x)]
        (if-let [ns (namespace x)] (str ns "/" n) n))))

(defn stringy? [x]
  (or (string? x) (keyword? x)))

(defn instance-any? [ts x]
  (some #(instance? % x) ts))

(defn rand-string []
  (.toString (java.util.UUID/randomUUID)))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn interpolate-keys [m]
  (into {}
    (mapcat (fn [[k v]]
              (if (coll? k)
                (map vector k (repeat v))
                [[k v]])) m)))

(defn require-keys [m ks]
  (if (map? ks)
    (require-keys (clojure.set/rename-keys m ks) (vals ks))
    (let [m (select-keys m ks)]
      (when (and (not-empty m) (not-any? nil? (vals m)))
        m))))

(defn close-with! [c val]
  (when-not (nil? val)
    (async/put! c val))
  (async/close! c))

(defn msecs-now []
  (clj-time.coerce/to-long (clj-time/now)))

(def utf-8 (Charset/forName "UTF-8"))

(defn get-utf8-bytes ^bytes [^String s]
  (.getBytes s ^Charset utf-8))

(defn channel-request! [m]
  (let [ch (async/chan)]
    (http/request m #(close-with! ch %))
    ch))

(defn merge-tagged
  ([ch->tag] (merge-tagged ch->tag nil))
  ([ch->tag buf-or-n]
   (let [out (async/chan buf-or-n)]
     (async/go-loop [cs (vec (keys ch->tag))]
       (if (pos? (count cs))
         (let [[v c] (async/alts! cs)]
           (if (nil? v)
             (recur (filterv #(not= c %) cs))
             (do (async/>! out [(ch->tag c) v])
                 (recur cs))))
         (async/close! out)))
     out)))
