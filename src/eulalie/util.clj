(ns eulalie.util
  (:require [clojure.set :refer [rename-keys]]))

(defn map-rewriter [spec]
  (fn [m]
    (reduce
     (fn [m [k v]]
       (cond
        (vector?  v) (update-in m [k] #(get-in % v))
        (keyword? v) (rename-keys m {k v})
        :else        (update-in m [k] v)))
     m
     (if (map? spec)
       spec
       (partition 2 spec)))))

(defn rewrite-map [m spec]
  ((map-rewriter spec) m))

(defmacro mapmap [body & ls]
  `(into {} (map (fn [[~'K ~'V]] ~body) ~@ls)))

(defmacro fn-> [& forms]
  `(fn [x#]
     (-> x# ~@forms)))

(defmacro fn->> [& forms]
  `(fn [x#]
     (->> x# ~@forms)))

(defmacro fn-some-> [& forms]
  `(fn [x#]
     (some-> x# ~@forms)))

(defmacro fn-some->> [& forms]
  `(fn [x#]
     (some->> x# ~@forms)))

(def stringify-keys
  (fn->> (mapmap [(if (keyword? K) (name K) (str K)) V])))
