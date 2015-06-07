(ns eulalie.util.xml
  (:require [camel-snake-kebab.core :as csk]
            [clojure.walk :as walk]
            [clojure.xml :as xml]))

(defn xml-map [mess]
  ;; I don't really know what people do
  (walk/postwalk
   (fn [x]
     (if (and (map? x) (:tag x))
       (let [{:keys [tag content]} x]
         {(csk/->kebab-case-keyword tag) content})
       x))
   mess))

(defn node-seq [x-map]
  (tree-seq map? #(apply concat (vals %)) x-map))

(defn child [container x]
  (some x (node-seq container)))

(def child-content (comp first child))

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
      {:type (csk/->kebab-case-keyword (child-content m :code))
       :message (child-content m :message)})))
