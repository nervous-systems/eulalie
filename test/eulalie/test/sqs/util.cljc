(ns eulalie.test.sqs.util
  (:require [eulalie.core :as eulalie]
            [eulalie.sqs]
            [eulalie.test.common :as test.common :refer [creds]]
            [glossop.core #? (:clj :refer :cljs :refer-macros) [go-catching <?]]))

(defn sqs! [target content & [req-overrides]]
  (go-catching
    (let [req (merge
               {:service :sqs
                :target  target
                :max-retries 0
                :body content
                :creds creds}
               req-overrides)]
      (:body (<? (test.common/issue-raw! req))))))

(defn get-queue-url! [q-name]
  (sqs! :get-queue-url {:queue-name q-name}))

(defn create-queue! [q-name]
  (go-catching
    (try
      (<? (sqs! :create-queue
                {:attrs {:maximum-message-size (* 128 1024)}
                 :queue-name q-name}))
      (catch
          #? (:clj clojure.lang.ExceptionInfo :cljs js/Error) e
          (if (-> e ex-data :type (= :queue-already-exists))
            (<? (get-queue-url! q-name))
            (throw e))))))

(defn delete-queue! [queue] (sqs! :delete-queue {:queue-url queue}))

(defn with-transient-queue! [f]
  (test.common/with-aws
    (fn [_creds]
      (go-catching
        (let [q-name (str "eulalie-transient-" (rand-int 0xFFFF))
              q-url  (<? (create-queue! q-name))]
          (try
            (<? (f {:name q-name :url q-url}))
            (finally
              (<? (delete-queue! q-url)))))))))

(defn purge-queue!  [queue] (sqs! :purge-queue  {:queue-url queue}))

(defn send-message! [queue & [attrs]]
  (sqs! :send-message
        {:message-body "Hello"
         :queue-url queue
         :attrs attrs}))
