(ns eulalie.test.instance-data
  (:require [eulalie.core :as eulalie]
            [eulalie.instance-data :as instance-data]
            [eulalie.platform :as platform]
            #?@ (:clj
                 [[eulalie.test.async :refer [deftest]]
                  [clojure.core.async :as async :refer [alt!]]
                  [clojure.test :refer [is]]
                  [glossop.core :refer [<? go-catching]]]
                 :cljs
                 [[cljs.core.async :as async]
                  [cemerick.cljs.test]]))
  #? (:cljs (:require-macros [glossop.macros :refer [<? go-catching]]
                             [eulalie.test.async.macros :refer [deftest]]
                             [cljs.core.async.macros :refer [alt!]]
                             [cemerick.cljs.test :refer [is]])))

(defn with-async [chan result-fn]
  (go-catching
    (let [result (<? chan)]
      (is (result-fn result)))))

(defn with-instance-data! [f]
  (go-catching
    (let [{:keys [error]} (<? (platform/http-get!
                               "http://instance-data.ec2.internal"))]
      (if-not error
        (<? (f))
        (println "Warning: Skipping test, can't retrieve instance data")))))

(deftest ^:integration ^:ec2 retrieve
  (with-instance-data!
    #(go-catching
       (let [data (<? (instance-data/retrieve!
                       [:latest :dynamic :instance-identity :document]))]
         (is (nil? data))))))

;; (defetst ^:integration ^:ec2 default-iam-credentials
;;   (go-catching
;;     (let [])))

