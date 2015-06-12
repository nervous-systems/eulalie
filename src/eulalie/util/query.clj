(ns ^{:doc "Utilities for query parameter-based services"}
  eulalie.util.query
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as csk-extras]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [eulalie.util :as util]
            [cheshire.core :as json]))

(defn nested-json-out [m]
  (->> m
       (csk-extras/transform-keys csk/->camelCaseString)
       json/encode))

(defn nested-json-in [s]
  (csk-extras/transform-keys
   csk/->kebab-case-keyword (json/decode s true)))

(defn enum-keys->matcher [keys-or-fns]
  (apply some-fn
         (for [v keys-or-fns]
           (if (keyword? v)
             #(if   (vector? %) (= v (first %)) (= v %))
             #(when (vector? %) (v %))))))

(defn translate-enums [req enum-keys]
  (let [matcher (enum-keys->matcher enum-keys)]
    (into req
      (for [[k v] req :when (matcher k)]
        [k (cond-> v (keyword? v) csk/->CamelCaseString)]))))

(defn join-key-paths [& segments]
  (vec (flatten (apply conj [] segments))))

(defn kv->dotted
  ;; AWS is all over the place.  sometimes key/value, sometimes Name/Value,
  ;; attribute vs. attributes, etc.
  [prefix k-name v-name attrs]
  (into {}
    (map-indexed
     (fn [i [k v]]
       {(join-key-paths prefix (inc i) k-name) k
        (join-key-paths prefix (inc i) v-name) v})
     attrs)))

(defn format-query-key [k]
  (cond (vector? k)
        (let [k (flatten k)]
          (str/join "." (map format-query-key k)))
        (string?  k) k
        (keyword? k) (csk/->CamelCaseString k)
        :else        (str k)))

(defn format-query-request [m]
  (walk/postwalk
   (fn [x]
     (if (map? x)
       (into {} (for [[k v] x]
                  [(format-query-key k)
                   (cond-> v (keyword? v) name)]))
       x))
   m))

(defn list->dotted [prefix l]
  (->> l
       (map-indexed
        (fn [i v] [(join-key-paths prefix (inc i)) v]))
       (into {})))

(defn map-list->dotted [prefix l]
  (->> l
       (mapcat
        (fn [i m]
          (for [[k v] m]
            [(join-key-paths prefix i k) v]))
        (iterate inc 1))
       (into {})))

(defmulti  expand-sequence (fn [[tag] value] tag))
(defmethod expand-sequence :list [[_ member-name] value]
  ;; The API is super messed up.  We're raising :all out of the list to avoid
  ;; confusion.  It's not supported for all lists (only both kinds of message
  ;; attributes)
  (let [value (if (= value :all) [:All] value)]
    (list->dotted member-name value)))

(defmethod expand-sequence :kv [[_ member-name k-name v-name] value]
  (kv->dotted member-name k-name v-name value))

(defmethod expand-sequence :maps [[_ prefix] value]
  (map-list->dotted prefix value))

(defn expand-sequences [body spec]
  (if-not spec
    body
    (reduce
     (fn [body [k sub-spec]]
       (if-let [v (body k)]
         (conj (dissoc body k)
               (expand-sequence sub-spec v))
         body))
     body spec)))

(defn default-request [{:keys [max-retries endpoint]} req]
  (merge {:max-retries max-retries
          :endpoint endpoint
          :method :post} req))

(defn prepare-query-request
  [{:keys [version] :as service} {:keys [body target] :as req}]
  (let [body (assoc body
                    :version version
                    :action (csk/->CamelCaseString target))]
    (-> (default-request service req)
        (assoc :body body)
        (assoc-in [:headers :content-type]
                  "application/x-www-form-urlencoded"))))

