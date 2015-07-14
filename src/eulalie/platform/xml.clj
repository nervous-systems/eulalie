(ns eulalie.platform.xml
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [clojure.xml :as xml]))

(defn xml-map [mess]
  ;; I don't really know what people do.  We don't care about attributes.
  (walk/postwalk
   (fn [x]
     (if (and (map? x) (:tag x))
       (let [{:keys [tag content]} x]
         {(csk/->kebab-case-keyword tag) content})
       x))
   mess))

(defn string->xml-map [^String resp]
  (some->
   resp
   (.getBytes "UTF-8")
   java.io.ByteArrayInputStream.
   xml/parse
   xml-map))
