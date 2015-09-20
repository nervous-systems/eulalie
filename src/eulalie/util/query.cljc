(ns ^{:doc "Utilities for query parameter-based services"}
  eulalie.util.query
  (:require [eulalie.core :as eulalie]
            [cemerick.url :as url]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as csk-extras]
            [eulalie.util.functor :refer [fmap]]
            [eulalie.util.service :as util.service]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [eulalie.util :as util]
            [eulalie.platform :as platform]
            [plumbing.core :refer [map-keys]]))

(defn nested-json-out [m]
  (->> m
       (csk-extras/transform-keys csk/->camelCaseString)
       platform/encode-json))

(defn nested-json-in [s]
  (csk-extras/transform-keys
   csk/->kebab-case-keyword (platform/decode-json s)))

(defn policy-key-out [k]
  (cond (and (keyword? k) (namespace k))
        (str (str/lower-case (namespace k))
             ":"
             (csk/->PascalCaseString (name k)))
        (string? k) k
        :else (csk/->PascalCaseString k)))

(defn policy-key-in [k]
  (let [k (cond-> k (keyword? k) name)]
    (cond
      (= "AWS" k) (name k) ;; eh
      (string? k)
      (let [segments (str/split (name k) #":" 2)]
        (apply keyword (map csk/->kebab-case-string segments)))
      :else (csk/->kebab-case-keyword k))))

(defn transform-policy-clause [xform-value {:keys [action effect principal] :as clause}]
  (cond->
      clause
    principal (assoc
               :principal
               (if (map? principal)
                 (map-keys name principal)
                 (name principal)))
    action    (assoc :action (fmap xform-value action))
    effect    (assoc :effect (xform-value effect))))

(defn transform-policy-statement [xform-value {:keys [statement] :as p}]
  (assoc
   p :statement
   (for [clause statement]
     (transform-policy-clause xform-value clause))))

(defn policy-json-out [policy]
  (->> policy
       (transform-policy-statement policy-key-out)
       (csk-extras/transform-keys policy-key-out)
       platform/encode-json))

(defn policy-json-in [s]
  (->> s
       platform/decode-json
       (csk-extras/transform-keys policy-key-in)
       (transform-policy-statement policy-key-in)))

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
        [k (cond-> v (keyword? v) csk/->PascalCaseString)]))))

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
        (keyword? k) (csk/->PascalCaseString k)
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

(defn prepare-message-attrs [a-name a-type a-value]
  (let [data-type ({:string :String
                    :number :Number
                    :binary :Binary} a-type a-type)
        value-type ({:Number :String} data-type data-type)]
    {:name a-name
     [:value :data-type] data-type
     [:value (str (name value-type) "Value")] a-value}))

(defn message-attrs->dotted
  [attrs & [{:keys [prefix] :or {prefix :message-attribute}}]]
  (map-list->dotted
   prefix
   (for [[a-name [a-type a-value]] attrs]
     (prepare-message-attrs a-name a-type a-value))))

(defn prepare-query-request
  [{:keys [version] :as service-defaults} {:keys [body target] :as req}]
  (let [body (assoc body
                    :version version
                    :action (csk/->PascalCaseString target))]
    (-> (util.service/default-request service-defaults req)
        (assoc :body body)
        (assoc-in [:headers :content-type]
                  "application/x-www-form-urlencoded"))))

(defmethod eulalie/transform-request-body :eulalie.service.generic/query-request
  [{:keys [body] :as req}]
  (-> body format-query-request url/map->query))
