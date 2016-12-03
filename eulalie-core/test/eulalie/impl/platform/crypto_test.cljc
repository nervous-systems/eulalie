(ns eulalie.impl.platform.crypto-test
  (:require [eulalie.impl.platform.crypto :as crypto]
            #?(:clj  [clojure.test :as t]
               :cljs [cljs.test :as t :include-macros true])))

(t/deftest str->crc32
  (t/is (= (crypto/str->crc32 "\u4dc0") 395713073)))
