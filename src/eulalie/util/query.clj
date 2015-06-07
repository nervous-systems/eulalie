(ns ^{:doc "Utilities for query parameter-based services"}
  eulalie.util.query
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as csk-extras]
            [clojure.walk :as walk]))

(defn translate-enums [req enum-keys]
  (reduce
   (fn [m k]
     (if-let [v (m k)]
       (assoc m k (csk/->CamelCaseString v))
       m))
   req enum-keys))

(defn kv->map
  ;; AWS is all over the place.  sometimes key/value, sometimes Name/Value,
  ;; attribute vs. attributes, etc.
  ([prefix k-name v-name attrs]
   (into {}
     (map-indexed
      (fn [i [k v]]
        (let [prefix (str prefix "." (inc i) ".")]
          {(str prefix k-name) (csk/->CamelCaseString k)
           (str prefix v-name) v}))
      attrs)))
  ([attrs]
   (merge
    (kv->map "attributes.entry" attrs "key" "value"))))

(defn format-query-request [m]
  (walk/postwalk
   (fn [x]
     (if (map? x)
       ;; No case translation for keys which are already strings
       (into {} (for [[k v] x]
                  [(cond-> k (keyword? k) csk/->CamelCaseString)
                   (cond-> v (keyword? v) name)]))
       x))
   m))

(defn list->map [prefix l]
  (->> l
       (map-indexed
        (fn [i v] [(str prefix "." (inc i)) v]))
       (into {})))

(defn map-list->map [prefix l]
  (->> l
       (mapcat
        (fn [i m]
          (let [key-name (str prefix "." i ".")]
            (for [[k v] m]
              [(str key-name k) v])))
        (iterate inc 1))
       (into {})))

(defmulti  expand-sequence (fn [[tag] value] tag))
(defmethod expand-sequence :list [[_ member-name opts] value]
  (list->map member-name
             (cond->> value
               (:enum opts)
               (map csk/->CamelCaseString))))
(defmethod expand-sequence :kv [[_ member-name k-name v-name opts] value]
  (kv->map member-name k-name v-name
           (cond->> value
             (:enum opts)
             (csk-extras/transform-keys
              csk/->CamelCaseString))))

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

