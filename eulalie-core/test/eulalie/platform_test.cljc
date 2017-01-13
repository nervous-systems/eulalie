(ns eulalie.platform-test
  (:require [clojure.test.check]
            [eulalie.platform :as platform]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test
             #?(:clj :refer :cljs :refer-macros) [defspec]]))

(defspec base64-roundtrip 50
  (prop/for-all* [gen/string]
    (fn [s]
      (= (platform/decode-base64 (platform/encode-base64 s)) s))))

(defspec json-keywords 30
  (prop/for-all* [(gen/map gen/keyword-ns gen/int)]
    (fn [m]
      (= (platform/decode-json (platform/encode-json m)) m))))
