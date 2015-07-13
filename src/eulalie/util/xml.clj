(ns eulalie.util.xml
  (:require [eulalie.core :as eulalie]
            [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [clojure.xml :as xml]
            [eulalie.util :as util]))

(defn xml-map [mess]
  ;; I don't really know what people do.  We don't care about attributes.
  (walk/postwalk
   (fn [x]
     (if (and (map? x) (:tag x))
       (let [{:keys [tag content]} x]
         {(csk/->kebab-case-keyword tag) content})
       x))
   mess))

(defn node-seq [x-map]
  (tree-seq map? #(apply concat (vals %)) x-map))

(defn children [container x]
  (filter x (node-seq container)))

(def child (comp first children))
(def content (comp ffirst vals))
(def child-content (comp content child))

(defn child-content->map [x node-names]
  (let [node-name->key
        (if (map? node-names)
          node-names
          (into {} (for [n node-names] [n n])))]
    (into {}
      (for [[node-name k] node-name->key]
        [k (child-content x node-name)]))))

(defn attrs->map
  [container & [{:keys [parent] :or {parent :entry}}]]
  (into {}
    (for [entry (children container parent)]
      [(csk/->kebab-case-keyword (child-content entry :key))
       (child-content entry :value)])))

(defn string->xml-map [^String resp]
  (some->
   resp
   (.getBytes "UTF-8")
   java.io.ByteArrayInputStream.
   xml/parse
   xml-map))

(defn parse-xml-error [body]
  (if-let [m (string->xml-map body)]
    (if (contains? m :internal-failure)
      {:type :internal-failure}
      {:type (-> m
                 (child-content :code)
                 csk/->kebab-case-string
                 (str/replace ".-" ".")
                 (util/from-last-match ".")
                 keyword)
       :message (child-content m :message)})))

(defn extract-response-value [target resp target->elem-spec]
  (if-let [[tag elem] (target->elem-spec target)]
    (case tag
      :one   (child-content resp elem)
      :many  (map content (children resp elem))
      :attrs (attrs->map resp))
    resp))

(defmethod eulalie/transform-response-error
  :eulalie.service.generic/xml-response [{:keys [body] :as resp}]
  (parse-xml-error body))
