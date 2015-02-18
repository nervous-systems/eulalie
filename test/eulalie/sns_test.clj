(ns eulalie.sns-test
  (:require [eulalie.sns :as sns]
            [eulalie.util :refer :all]
            [clojure.test :refer :all]
            [eulalie.test-util :refer :all]))

(deftest ^:integration list-subscriptions
  (is (not (:error
            (<?! (eulalie/issue-request!
                  sns/service
                  creds
                  {:target :list-subscriptions}))))))
