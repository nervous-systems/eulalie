(ns eulalie.service.dynamo-test
  (:require #?(:cljs [clojure.test.check])
            [eulalie.service.dynamo]
            [eulalie.service.test.util :as test.util]
            [eulalie.service.dynamo.test.common :refer [targets]]
            [clojure.test.check.clojure-test
             #?(:clj :refer :cljs :refer-macros) [defspec]]))

(defspec req+resp #?(:clj 100 :cljs 20)
  (test.util/request-roundtrip-property :dynamo targets))
