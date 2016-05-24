(ns eulalie.test.creds
  (:require [eulalie.creds :as creds]
            [eulalie.test.common #? (:clj :refer :cljs :refer-macros) [deftest is]]
            [glossop.core #? (:clj :refer :cljs :refer-macros) [go-catching <?]]
            #? (:clj
                [clojure.core.async :as async]
                :cljs
                [cljs.core.async :as async])))

(deftest expiring-creds
  (let [expirations (async/to-chan
                     [{:expiration 1}
                      {:expiration 5}])
        creds (creds/expiring-creds (constantly expirations) {:threshold 0})]
    (go-catching
      (is (= 1 (:expiration (<? (creds/creds->credentials creds 0)))))
      (is (= 1 (:expiration (<? (creds/creds->credentials creds 0)))))
      (is (= 5 (:expiration (<? (creds/creds->credentials creds 1))))))))


