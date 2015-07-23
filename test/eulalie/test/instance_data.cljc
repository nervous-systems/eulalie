(ns eulalie.test.instance-data
  (:require [eulalie.core :as eulalie]
            [eulalie.instance-data :as instance-data]
            [eulalie.platform :as platform]
            #?@ (:clj
                 [[eulalie.test.async :refer [deftest]]
                  [clojure.test :refer [is]]
                  [glossop.core :refer [<? go-catching]]]
                 :cljs
                 [[cemerick.cljs.test]]))
  #? (:cljs (:require-macros [glossop.macros :refer [<? go-catching]]
                             [eulalie.test.async.macros :refer [deftest]]
                             [cemerick.cljs.test :refer [is]])))

(defn with-instance-data! [f]
  (go-catching
    (let [{:keys [error]}
          (<? (platform/http-get! "http://instance-data.ec2.internal"))]
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
