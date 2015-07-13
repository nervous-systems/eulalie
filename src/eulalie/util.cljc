(ns eulalie.util
  (:require [clojure.set :refer [rename-keys]]
            [clojure.string :as string]
            [glossop.misc :refer [not-neg]]))

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

(defn rand-string []
  (str (rand-int 0xFFFFFFFF)))

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
