(ns eulalie.sns-test
  (:require [eulalie]
            [eulalie.util.xml :as x]
            [eulalie.sns :refer :all]
            [eulalie.test-util :refer :all]
            [clojure.test :refer :all]))

(def aws-account-id "510355070671")

(defn sns!! [target body]
  (let [{:keys [error body]}
        (eulalie/issue-request!!
         {:service :sns
          :creds   creds
          :target  target
          :body    body})]
    (if error
      (throw (ex-info (name (:type error)) error))
      body)))

(defn create-topic* []
  (x/child-content
   (sns!! :create-topic {:name "the-best-topic"})
   :topic-arn))

(deftest add-permission+
  (is (:add-permission-response
       (sns!!
        :add-permission
        {:accounts [aws-account-id]
         :actions [:publish :get-topic-attributes]
         :label "eulalie-add-permission-test"
         :topic-arn (create-topic*)}))))

(def gcm-api-key (get (System/getenv) "GCM_API_KEY"))

(defn create-gcm-application* []
  (x/child-content
   (sns!!
    :create-platform-application
    {:attrs {:platform-credential gcm-api-key}
     :name "the-best-application"
     :platform :GCM})
   :platform-application-arn))

(deftest create-platform-application+
  (is (= "arn:"
         (-> (create-gcm-application*)
             (subs 0 4)))))

(deftest set-platform-application-attributes+
  (let [arn (create-gcm-application*)]
    (is (sns!! :set-platform-application-attributes
               {:platform-application-arn arn
                :attrs {:success-feedback-sample-rate 50}}))))

(deftest get-platform-application-attributes+
  (let [arn (create-gcm-application*)]
    (sns!! :get-platform-application-attributes
           {:platform-application-arn arn})))
