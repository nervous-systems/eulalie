(ns eulalie.util.xml
  (:require [camel-snake-kebab.core :as csk]
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

(defn child-content->map [x node-name->key]
  (into {}
    (for [[node-name k] node-name->key]
      [k (child-content x node-name)])))

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
