(ns eulalie.util
  (:require [glossop.misc :refer [not-neg]]))

(defn to-first-match [^String hay ^String needle]
  (or (some->> (.indexOf hay needle) not-neg (subs hay 0)) hay))

(defn from-first-match [^String hay ^String needle]
  (or (some->> (.indexOf hay needle) not-neg inc (subs hay)) hay))

(defn from-last-match [^String hay ^String needle]
  (or (some->> (.lastIndexOf hay needle) not-neg inc (subs hay)) hay))

(defn to-last-match [^String hay ^String needle]
  (or (some->> (.lastIndexOf hay needle) not-neg (subs hay 0)) hay))

(defn env! [s & [default]]
  #? (:clj
      (get (System/getenv) s default)
      :cljs
      (or (aget js/process "env" s) default)))
