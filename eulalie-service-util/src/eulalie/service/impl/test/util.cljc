(ns eulalie.service.impl.test.util
  (:require [cheshire.core :as cheshire]))

(defn un-ns [m]
  (into {}
    (for [[k v] m :when (not (namespace k))]
      [k v])))

(defn- edn->json [form]
  #?(:cljs (js/JSON.stringify (clj->js form))
     :clj  (cheshire/encode form)))
