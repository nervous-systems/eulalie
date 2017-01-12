(ns eulalie.platform-test
  (:require #?(:cljs [clojure.test.check])
            [eulalie.platform :as platform]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :as ct]))

(ct/defspec base64-roundtrip
  (prop/for-all* [gen/string]
    (fn [s]
      (= (platform/decode-base64 (platform/encode-base64 s)) s))))

(ct/defspec json-keywords 50
  (prop/for-all* [(gen/map gen/keyword-ns gen/int)]
    (fn [m]
      (= (platform/decode-json (platform/encode-json m)) m))))
