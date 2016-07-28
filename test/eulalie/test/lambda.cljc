(ns eulalie.test.lambda
  (:require [eulalie.lambda.util :as lu]
            #? (:clj [clojure.test :refer [testing]]
                :cljs [cljs.test :refer-macros [testing]])
            [eulalie.test.common :refer [issue-raw! creds]]
            [glossop.core #? (:clj :refer :cljs :refer-macros) [go-catching <?]]
            [eulalie.test.common :as test.common
             #? (:clj :refer :cljs :refer-macros) [deftest is]]
            [eulalie.util :refer [env!]]))

(def lambda-role-arn (env! "LAMBDA_ROLE_ARN"))
(def function-zipped "UEsDBAoAAAAAAEBL+0iHxOHaUwAAAFMAAAAHAAAAdGVzdC5qc2V4cG9ydHMuaGFuZGxlciA9IChldmVudCwgY29udGV4dCwgY2FsbGJhY2spID0+IHsgY2FsbGJhY2sobnVsbCwgIkhlbGxvIHdvcmxkIik7IH07UEsBAhQACgAAAAAAQEv7SIfE4dpTAAAAUwAAAAcAAAAAAAAAAAAAAAAAAAAAAHRlc3QuanNQSwUGAAAAAAEAAQA1AAAAeAAAAAAA")
(defn function-name [] (str "eulalie-test-" (rand-int 2147483647))) ;JVM Integer/MAX_VALUE

(deftest ^:integration ^:aws create-test-function
         (if (not-empty (:secret-key creds))
           (let [function-name (function-name)]
             (go-catching
               (testing "Create function"
                 (is (= 201 (get-in (<? (issue-raw! {:creds creds
                                                     :service :lambda
                                                     :target :create-function
                                                     :body {:code {:zip-file function-zipped}
                                                            :function-name function-name
                                                            :handler "test.handler"
                                                            :role lambda-role-arn
                                                            :runtime "nodejs4.3"}}))
                                    [:response :status]))))
               (testing "Invoke function"
                 (is (= [:ok "Hello world"]
                        (<? (lu/invoke! creds function-name :request-response {})))))
               (testing "Delete function"
                 (is (= 204 (get-in (<? (issue-raw! {:creds creds
                                                     :service :lambda
                                                     :method :delete
                                                     :target :delete-function
                                                     :body {:function-name function-name}}))
                                    [:response :status]))))))
           (println "Warning: Skipping remote test due to unset AWS_SECRET_KEY")))
