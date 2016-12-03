(ns eulalie.platform-test
  (:require [eulalie.platform :as platform]
            #?(:clj  [clojure.test :as t]
               :cljs [cljs.test :as t :include-macros true])))

(t/deftest encode-base64
  (t/is (= (platform/decode-base64 (platform/encode-base64 "\u4dc0"))
           "\u4dc0")))
