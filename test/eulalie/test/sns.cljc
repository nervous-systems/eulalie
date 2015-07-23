(ns eulalie.test.sns
  (:require [eulalie.core :as eulalie]
            [eulalie.sns]
            [eulalie.util.xml :as x]
            [eulalie.test.common :as test.common :refer [creds]]
            #?@ (:clj
                 [[clojure.test :refer [is]]
                  [eulalie.test.async :refer [deftest]]
                  [glossop.core :refer [<? go-catching]]]
                 :cljs
                 [[cemerick.cljs.test]]))
  #? (:cljs (:require-macros [eulalie.test.async.macros :refer [deftest]]
                             [cemerick.cljs.test :refer [is]]
                             [glossop.macros :refer [<? go-catching]])))

(defn sns! [target content & [req-overrides]]
  (go-catching
    (let [req (merge
               {:service :sns
                :target  target
                :max-retries 0
                :body content
                :creds creds}
               req-overrides)]
      (:body (<? (test.common/issue-raw! req))))))

(defn create-topic! []
  (sns! :create-topic {:name "the-best-topic"}))

(deftest ^:integration ^:aws create-topic
  (go-catching
    (is (= "arn:" (some-> (create-topic!) <? (subs 0 4))))))

(deftest ^:integration ^:aws add-permission
  (go-catching
    (let [response
          (->
           (sns!
            :add-permission
            {:accounts [test.common/aws-account-id]
             :actions [:publish :get-topic-attributes]
             :label "eulalie-add-permission-test"
             :topic-arn (<? (create-topic!))})
           <?
           :add-permission-response)]
      (is response))))

(defn create-gcm-application! []
  (sns!
   :create-platform-application
   {:attrs {:platform-credential test.common/gcm-api-key}
    :name "the-best-application"
    :platform :GCM}))

(def gcm-token "XYZ")

(defn create-platform-endpoint! [arn]
  (sns! :create-platform-endpoint
         {:platform-application-arn arn :token gcm-token}))

(deftest ^:integration ^:aws create-platform-application
  (go-catching
    (is (= "arn:"
           (some-> (create-gcm-application!)
                   <?
                   (subs 0 4))))))

(deftest ^:integration ^:aws set-platform-application-attributes
  (go-catching
    (let [arn (<? (create-gcm-application!))]
      (<? (sns! :set-platform-application-attributes
                {:platform-application-arn arn
                 :attrs {:success-feedback-sample-rate 50}}))
      (is (:success-feedback-sample-rate
           (<? (sns! :get-platform-application-attributes
                     {:platform-application-arn arn})))))))

(deftest ^:integration ^:aws delete-platform-application+
  (go-catching
    (let [arn (<? (create-gcm-application!))]
      (is (<? (sns! :delete-platform-application
                    {:platform-application-arn arn}))))))

(defn create-platform-application! [name]
  (sns!
   :create-platform-application
   {:platform :GCM :name name
    :attrs {:platform-credential test.common/gcm-api-key}}))

(defn with-transient-app! [f]
  (go-catching
    (let [arn (<? (create-platform-application!
                   (str "eulalie-transient-" (rand-int 0xFFFF))))]
      (try
        (<? (f arn))
        (finally
          (<? (sns! :delete-platform-application
                    {:platform-application-arn arn})))))))

(defn with-subscription! [f]
  (with-transient-app!
    (fn [p-arn]
      (go-catching
        (let [e-arn (<? (create-platform-endpoint! p-arn))]
          (-> (sns!
               :subscribe
               {:topic-arn (<? (create-topic!))
                :protocol  :application
                :endpoint  e-arn})
              <?
              f
              <?))))))

(deftest ^:integration ^:aws list-subscriptions
  (with-subscription!
    (fn [s-arn]
      (go-catching
        (is (some map? (<? (sns! :list-subscriptions {}))))))))

(deftest ^:integration ^:aws get-subscription-attributes
  (with-subscription!
    (fn [s-arn]
      (go-catching
        (is (= "application"
               (:protocol
                (<? (sns! :get-subscription-attributes
                          {:subscription-arn s-arn})))))))))

(deftest ^:integration ^:aws publish
  (go-catching
    (is (string?
         (<? (sns!
              :publish
              {:topic-arn (<? (create-topic!))
               :subject "Hello"
               :message {:default "OK" :GCM {:data {:message "This is the GCM"}}}}))))))

(deftest ^:integration ^:aws get-topic-attributes
  (go-catching
    (let [arn (<? (create-topic!))]
      (is (map? (:policy (<? (sns! :get-topic-attributes {:topic-arn arn}))))))))

(deftest ^:integration ^:aws get-endpoint-attributes
  (with-transient-app!
    (fn [p-arn]
      (go-catching
        (let [e-arn (<? (create-platform-endpoint! p-arn))]
          (is (= gcm-token
                 (:token (<? (sns! :get-endpoint-attributes
                                   {:endpoint-arn e-arn}))))))))))

(deftest ^:integration ^:aws list-endpoints-by-platform-application
  (with-transient-app!
    (fn [p-arn]
      (go-catching
        (let [e-arn (<? (create-platform-endpoint! p-arn))
              arns
              (->> (sns! :list-endpoints-by-platform-application
                         {:platform-application-arn p-arn})
                   <?
                   (map :arn)
                   (into #{}))]
          (is (arns e-arn)))))))
