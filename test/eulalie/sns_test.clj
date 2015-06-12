(ns eulalie.sns-test
  (:require [eulalie]
            [eulalie.sns]
            [eulalie.util.xml :as x]
            [eulalie.test-util :refer :all]
            [clojure.test :refer :all]))

(def sns!! (make-issuer :sns))

(defn create-topic* []
  (sns!! :create-topic {:name "the-best-topic"}))

(deftest add-permission+
  (is (:add-permission-response
       (sns!!
        :add-permission
        {:accounts [aws-account-id]
         :actions [:publish :get-topic-attributes]
         :label "eulalie-add-permission-test"
         :topic-arn (create-topic*)}))))

(defn create-gcm-application* []
  (sns!!
   :create-platform-application
   {:attrs {:platform-credential gcm-api-key}
    :name "the-best-application"
    :platform :GCM}))

(def gcm-token "XYZ")

(defn create-platform-endpoint* [arn]
  (sns!! :create-platform-endpoint
         {:platform-application-arn arn :token gcm-token}))

(deftest create-platform-application+
  (is (= "arn:"
         (-> (create-gcm-application*)
             (subs 0 4)))))

(deftest set-platform-application-attributes+
  (let [arn (create-gcm-application*)]
    (sns!! :set-platform-application-attributes
           {:platform-application-arn arn
            :attrs {:success-feedback-sample-rate 50}})
    (is (:success-feedback-sample-rate
         (sns!! :get-platform-application-attributes
                {:platform-application-arn arn})))))

(deftest delete-platform-application+
  (let [arn (create-gcm-application*)]
    (is (sns!! :delete-platform-application
               {:platform-application-arn arn}))))

(defn create-platform-application* [name]
  (sns!!
   :create-platform-application
   {:platform :GCM :name name :attrs {:platform-credential gcm-api-key}}))

(defn with-transient-app [f]
  (let [arn (create-platform-application*
             (str "eulalie-transient-" (rand-int 0xFFFF)))]
    (try
      (f arn)
      (finally
        (sns!! :delete-platform-application
               {:platform-application-arn arn})))))

(deftest list-platform-applications+
  (with-transient-app
    (fn [arn]
      (is ((->> (sns!! :list-platform-applications {})
                (map :arn)
                (into #{})) arn)))))

(defn with-subscription [f]
  (with-transient-app
    (fn [p-arn]
      (let [e-arn (create-platform-endpoint* p-arn)]
        (f (sns!! :subscribe
                  {:topic-arn (create-topic*)
                   :protocol  :application
                   :endpoint  e-arn}))))))

(deftest list-subscriptions+
  (with-subscription
    (fn [s-arn]
      (is (some map? (sns!! :list-subscriptions {}))))))

(deftest get-subscription-attributes+
  (with-subscription
    (fn [s-arn]
      (is (= "application"
             (:protocol
              (sns!! :get-subscription-attributes
                     {:subscription-arn s-arn})))))))

(deftest publish+
  (is (string?
       (sns!!
        :publish
        {:topic-arn (create-topic*)
         :subject "Hello"
         :message {:default "OK" :GCM {:data {:message "This is the GCM"}}}}))))

(deftest get-topic-attributes+
  (let [arn (create-topic*)]
    (is (map? (:policy (sns!! :get-topic-attributes {:topic-arn arn}))))))

(deftest set-topic-attributes+
  (let [arn (create-topic*)]
    (is (map? (sns!! :set-topic-attributes
                     {:topic-arn arn :name :policy :value {}})))))

(deftest get-endpoint-attributes+
  (with-transient-app
    (fn [p-arn]
      (let [e-arn (create-platform-endpoint* p-arn)]
        (is (= gcm-token
               (:token (sns!! :get-endpoint-attributes
                              {:endpoint-arn e-arn}))))))))

(deftest list-endpoints-by-platform-application+
  (with-transient-app
    (fn [p-arn]
      (let [e-arn (create-platform-endpoint* p-arn)
            arns
            (->> (sns!! :list-endpoints-by-platform-application
                        {:platform-application-arn p-arn})
                 (map :arn)
                 (into #{}))]
        (is (arns e-arn))))))
