(ns eulalie.test.creds
  (:require [eulalie.creds :as creds]
            [eulalie.platform.time :as platform.time]
            [eulalie.test.platform.time :refer [with-canned-time set-time]]
            [eulalie.test.common #? (:clj :refer :cljs :refer-macros) [deftest is]]
            [glossop.core #? (:clj :refer :cljs :refer-macros) [go-catching <?]]
            #? (:clj
                [clojure.core.async :as async]
                :cljs
                [cljs.core.async :as async])))

;; We're building a ziggurat to the sky
(deftest expiring-creds
  (let [expirations (async/to-chan
                     [{:expiration 1}
                      {:expiration 5}])
        creds (creds/expiring-creds (constantly expirations) {:threshold 0})
        expiry #(-> creds :current deref :expiration)]
    (go-catching
      (let [creds (<? (creds/creds->credentials creds))]
        (is (= 1 (expiry)))
        (<? (with-canned-time 0
              (fn []
                (go-catching
                  (let [creds (<? (creds/creds->credentials creds))]
                    (is (= 1 (expiry)))
                    (set-time 1)
                    (let [creds (<? (creds/creds->credentials creds))]
                      (is (= 5 (expiry)))))))))))))


