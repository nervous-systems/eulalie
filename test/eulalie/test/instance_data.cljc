(ns eulalie.test.instance-data
  (:require [eulalie.core :as eulalie]
            [eulalie.instance-data :as instance-data]
            [eulalie.http :as http]
            [glossop.core #? (:clj :refer :cljs :refer-macros) [go-catching <?]]
            [eulalie.test.common :as test.common
             #? (:clj :refer :cljs :refer-macros) [deftest is]]))

(defn with-instance-data! [f]
  (go-catching
    (let [{:keys [error]}
          (<? (http/get! "http://instance-data.ec2.internal"))]
      (if-not error
        (<? (f))
        (println "Warning: Skipping test, can't retrieve instance data")))))

(deftest ^:integration ^:ec2 identity-key
  (with-instance-data!
    #(go-catching
       (is (string? (<? (instance-data/identity-key! :private-ip)))))))

(deftest ^:integration ^:ec2 default-iam-credentials
  (with-instance-data!
    #(go-catching
       (is ((every-pred :secret-key :access-key :expiration)
            (<? (instance-data/default-iam-credentials!)))))))
