(ns ^{:doc "Utilities for query parameter-based services"}
  eulalie.util.query
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as csk-extras]
            [eulalie.util :as util]
            [clojure.string :as str]
            [clojure.walk :as walk]))

(defn translate-enums [req enum-keys]
  (reduce
   (fn [m k]
     (if-let [v (m k)]
       (assoc m k (csk/->CamelCaseString v))
       m))
   req enum-keys))

(defn kv->dotted
  ;; AWS is all over the place.  sometimes key/value, sometimes Name/Value,
  ;; attribute vs. attributes, etc.
  [prefix k-name v-name attrs]
  (into {}
    (map-indexed
     (fn [i [k v]]
       {[prefix (inc i) k-name] k
        [prefix (inc i) v-name] v})
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
        (fn [i v] [[prefix (inc i)] v]))
       (into {})))

(defn map-list->dotted [prefix l]
  (->> l
       (mapcat
        (fn [i m]
          (for [[k v] m]
            [[prefix i k] v]))
        (iterate inc 1))
       (into {})))

(defmulti  expand-sequence (fn [[tag] value] tag))
(defmethod expand-sequence :list [[_ member-name opts] value]
  ;; The API is super messed up.  We're raising :all out of the list to avoid
  ;; confusion.  It's not supported for all lists (only both kinds of message
  ;; attributes)
  (let [value (if (= value :all) [:All] value)]
    (list->dotted
     member-name
     (cond->> value (:enum opts) (map csk/->CamelCaseString)))))

(defmethod expand-sequence :kv [[_ member-name k-name v-name opts] value]
  (kv->dotted
   member-name k-name v-name
   (cond->> value
     (:enum opts)
     (csk-extras/transform-keys csk/->CamelCaseString))))

(defmethod expand-sequence :maps [[_ prefix opts] value]
  (map-list->dotted
   prefix
   (cond->> value
     (:enum opts)
     (csk-extras/transform-keys csk/->CamelCaseString))))

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

