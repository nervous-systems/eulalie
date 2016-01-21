(ns eulalie.test.cognito-sync
  (:require [clojure.walk :as walk]
            [glossop.core #? (:clj :refer :cljs :refer-macros) [go-catching <?]]
            [eulalie.test.common #? (:clj :refer :cljs :refer-macros) [deftest is]]
            [#? (:clj clojure.core.async :cljs cljs.core.async) :as async]
            [eulalie.core :as eulalie]
            [eulalie.cognito.util :refer [get-open-id-token-for-developer-identity!]]
            [eulalie.cognito-sync]
            [eulalie.test.common :as test.common]
            [eulalie.util :refer [env!]]
            [plumbing.core :refer [dissoc-in]]))

(def cognito-developer-provider-name (env! "COGNITO_DEVELOPER_PROVIDER_NAME"))
(def cognito-identity-pool-id (env! "COGNITO_IDENTITY_POOL_ID"))
(def cognito-role-arn (env! "COGNITO_ROLE_ARN"))
(def cognito-identity-id (env! "COGNITO_IDENTITY_ID"))

(let [chars (map char (range 97 112))]
  (defn random-id []
    (reduce str (take 16 (repeatedly #(rand-nth chars))))))

(defn cognito-sync! [creds target content & [req-overrides]]
  (go-catching
    (let [req (merge
               {:service :cognito-sync
                :target target
                :max-retries 0
                :body content
                :creds creds}
               req-overrides)]
      (:body (<? (test.common/issue-raw! req))))))

(defn test-params [& [m]]
  (merge
   {:identity-pool-id cognito-identity-pool-id
    :dataset-name (str "test-dataset-" (random-id))
    :identity-id cognito-identity-id}
   m))

(defn delete-datasets! [creds]
  (go-catching
    (let [datasets (->> (cognito-sync! creds :list-datasets (test-params))
                        <?
                        :datasets
                        (map :dataset-name))
          deletes (for [dataset datasets]
                    (cognito-sync!
                     creds :delete-dataset
                     (test-params {:dataset-name dataset})))]
      (<? (async/into [] (async/merge deletes))))))

(deftest list-records-test!
  (test.common/with-aws
    (fn [creds]
      (go-catching
        (let [response (<? (cognito-sync! creds :list-records (test-params)))]
          (is (= 0 (:dataset-sync-count response)))
          (is (= 0 (:count response)))
          (is (string? (:sync-session-token response))))))))

(defn update-records! [creds token test-params & [id]]
  (cognito-sync!
   creds :update-records
   (assoc test-params
     :sync-session-token token
     :record-patches [{:key (or id (random-id))
                       :op "replace"
                       :sync-count 0
                       :value "bar"}])))

(deftest update-records-test!
  (test.common/with-aws
    (fn [creds]
      (go-catching
        (<? (delete-datasets! creds))
        (let [params (test-params)
              {token :sync-session-token :as x}
              (<? (cognito-sync! creds :list-records params))
              {:keys [records]} (<? (update-records! creds token params))]
          (is (= 1 (count records)))
          (let [[{:keys [value sync-count]}] records]
            (is (= value "bar"))
            (is (= sync-count 1))))))))

(deftest retrieve-records-test!
  (test.common/with-aws
    (fn [creds]
      (go-catching
        (<? (delete-datasets! creds))
        (let [params (test-params)
              {token :sync-session-token}
              (<? (cognito-sync! creds :list-records params))
              _ (<? (update-records! creds token params))
              {:keys [records]} (<? (cognito-sync! creds :list-records params))]
          (is (= 1 (count records)))
          (let [[{:keys [value sync-count]}] records]
            (is (= value "bar"))
            (is (= sync-count 1))))))))
