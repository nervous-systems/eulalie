(ns eulalie.service.dynamo-test
  (:require #?(:cljs [clojure.test.check])
            [eulalie.service :as service]
            [eulalie.service.dynamo]
            [#?(:clj clojure.spec.test :cljs cljs.spec.test) :as stest]
            [clojure.test.check.clojure-test
             #?(:clj :refer :cljs :refer-macros) [defspec]]
            [clojure.test.check.properties :as prop]
            [#?(:clj clojure.spec :cljs cljs.spec) :as s]
            [eulalie.service.impl.test.util :refer [un-ns]]
            [clojure.walk :as walk]))

(stest/instrument)

(def ^:private request (s/gen :eulalie.service.dynamo.request/body))

(def request-roundtrip-property
  (prop/for-all* [request]
    (fn [body]
      (let [req       {:eulalie.request/service :eulalie.service/dynamo
                       :eulalie.service.dynamo.request/body body}
            xformed   (service/transform-request-body req)
            resp-body (service/transform-response-body
                       #:eulalie.response {:eulalie/request req
                                           :status          200
                                           :body            xformed})]
        (= resp-body (un-ns (walk/keywordize-keys body)))))))

(defspec req+resp #?(:clj 100 :cljs 20)
  request-roundtrip-property)
