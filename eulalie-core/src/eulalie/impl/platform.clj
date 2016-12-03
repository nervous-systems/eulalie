(ns eulalie.impl.platform
  (:require [clojure.string :as str])
  (:import [java.nio.charset Charset]
           [javax.xml.bind DatatypeConverter]))

(let [utf-8 (Charset/forName "UTF-8")]
  (defn utf8-bytes ^bytes [^String s]
    (.getBytes s ^Charset utf-8)))

(def byte-count (comp count utf8-bytes))

(defn bytes->hex-str [bytes]
  (str/lower-case (DatatypeConverter/printHexBinary bytes)))

(defn env [s & [default]]
  (get (System/getenv) (name s) default))
