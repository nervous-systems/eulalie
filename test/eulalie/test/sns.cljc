(ns eulalie.test.sns
  (:require [eulalie.core :as eulalie]
            [eulalie.sns]
            [eulalie.util.xml :as x]
            [glossop.core #? (:clj :refer :cljs :refer-macros) [go-catching <?]]
            [eulalie.test.common :as test.common
             #? (:clj :refer :cljs :refer-macros) [deftest is]]))

(defn sns! [creds target content & [req-overrides]]
  (go-catching
    (let [req (merge
               {:service :sns
                :target  target
                :max-retries 0
                :body content
                :creds creds}
               req-overrides)]
      (:body (<? (test.common/issue-raw! req))))))

(defn create-topic! [creds]
  (sns! creds :create-topic {:name "the-best-topic"}))

(deftest ^:integration ^:aws create-topic
  (test.common/with-aws
    (fn [creds]
      (go-catching
        (is (= "arn:" (some-> (create-topic! creds) <? (subs 0 4))))))))

(deftest ^:integration ^:aws add-permission
  (test.common/with-aws
    (fn [creds]
      (go-catching
        (if (not-empty test.common/aws-account-id)
          (is (-> (sns!
                   creds
                   :add-permission
                   {:accounts [test.common/aws-account-id]
                    :actions [:publish :get-topic-attributes]
                    :label "eulalie-add-permission-test"
                    :topic-arn (<? (create-topic! creds))})
                  <?
                  :add-permission-response))
          (println "Warning: Skipping test due to absence of AWS_ACCOUNT_ID var"))))))

(defn create-gcm-application! [creds]
  (sns!
   creds
   :create-platform-application
   {:attrs {:platform-credential test.common/gcm-api-key}
    :name "the-best-application"
    :platform :GCM}))

(def gcm-token "XYZ")

(defn create-platform-endpoint! [creds arn]
  (sns! creds :create-platform-endpoint
        {:platform-application-arn arn :token gcm-token}))

(deftest ^:integration ^:aws create-platform-application
  (test.common/with-aws
    (fn [creds]
      (go-catching
        (is (= "arn:"
               (some-> (create-gcm-application! creds)
                       <?
                       (subs 0 4))))))))

(deftest ^:integration ^:aws set-platform-application-attributes
  (test.common/with-aws
    (fn [creds]
      (go-catching
        (let [arn (<? (create-gcm-application! creds))]
          (<? (sns! creds
                    :set-platform-application-attributes
                    {:platform-application-arn arn
                     :attrs {:success-feedback-sample-rate 50}}))
          (is (:success-feedback-sample-rate
               (<? (sns! creds
                         :get-platform-application-attributes
                         {:platform-application-arn arn})))))))))

(deftest ^:integration ^:aws delete-platform-application+
  (test.common/with-aws
    (fn [creds]
      (go-catching
        (let [arn (<? (create-gcm-application! creds))]
          (is (<? (sns! creds
                        :delete-platform-application
                        {:platform-application-arn arn}))))))))

(defn create-platform-application! [creds name]
  (sns!
   creds
   :create-platform-application
   {:platform :GCM :name name
    :attrs {:platform-credential test.common/gcm-api-key}}))

(defn with-transient-app! [f]
  (test.common/with-aws
    (fn [creds]
      (go-catching
        (let [arn (<? (create-platform-application!
                       creds
                       (str "eulalie-transient-" (rand-int 0xFFFF))))]
          (try
            (<? (f creds arn))
            (finally
              (<? (sns! creds
                        :delete-platform-application
                        {:platform-application-arn arn})))))))))

(deftest ^:integration ^:aws publish
  (test.common/with-aws
    (fn [creds]
      (go-catching
        (is (-> (sns!
                 creds
                 :publish
                 {:topic-arn (<? (create-topic! creds))
                  :subject "Hello"
                  :message {:default "OK" :GCM {:data {:message "This is the GCM"}}}
                  :attrs {:name [:string "OK"]}})
                <?
                string?))))))

(deftest ^:integration ^:aws get-topic-attributes
  (test.common/with-aws
    (fn [creds]
      (go-catching
        (let [arn (<? (create-topic! creds))]
          (is (map? (:policy
                     (<? (sns! creds :get-topic-attributes {:topic-arn arn}))))))))))

(deftest ^:integration ^:aws get-endpoint-attributes
  (with-transient-app!
    (fn [creds p-arn]
      (go-catching
        (let [e-arn (<? (create-platform-endpoint! creds p-arn))]
          (is (= gcm-token
                 (:token (<? (sns! creds
                                   :get-endpoint-attributes
                                   {:endpoint-arn e-arn}))))))))))

(deftest ^:integration ^:aws list-endpoints-by-platform-application
  (with-transient-app!
    (fn [creds p-arn]
      (go-catching
        (let [e-arn (<? (create-platform-endpoint! creds p-arn))
              arns
              (->> (sns! creds
                         :list-endpoints-by-platform-application
                         {:platform-application-arn p-arn})
                   <?
                   (map :arn)
                   (into #{}))]
          (is (arns e-arn)))))))
