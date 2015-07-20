(ns eulalie.test.creds
  (:require [eulalie.creds :as creds]
            [eulalie.platform.time :as platform.time]
            [eulalie.test.platform.time :refer [with-canned-time set-time]]
            #?@ (:clj
                 [[clojure.core.async :as async]
                  [clojure.test :refer [is]]
                  [eulalie.test.async :refer [deftest]]
                  [glossop.core :refer [go-catching <?]]]
                 :cljs
                 [[cemerick.cljs.test]
                  [cljs.core.async :as async]]))
  #? (:cljs
      (:require-macros [glossop.macros :refer [<? go-catching]]
                       [eulalie.test.async.macros :refer [deftest]]
                       [cemerick.cljs.test :refer [is]])))

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
