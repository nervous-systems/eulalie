(ns eulalie.sqs.test-util
  (:require [eulalie.test-util :as test-util]))

(def sqs!! (test-util/make-issuer :sqs))

(defn get-queue-url* [q-name]
  (sqs!! :get-queue-url {:queue-name q-name}))

(defn create-queue* [q-name]
  (try
    (sqs!! :create-queue
           {:attrs {:maximum-message-size (* 128 1024)}
            :queue-name q-name})
    (catch clojure.lang.ExceptionInfo e
      (if (-> e ex-data :type (= :queue-already-exists))
        (get-queue-url* q-name)
        (throw e)))))

(defn delete-queue* [queue] (sqs!! :delete-queue {:queue-url queue}))

(defn with-transient-queue [f]
  (let [q-name (str "eulalie-transient-" (rand-int 0xFFFF))
        q-url  (create-queue* q-name)]
    (try
      (f {:name q-name :url q-url})
      (finally
        (delete-queue* q-url)))))

(defn purge-queue*  [queue] (sqs!! :purge-queue  {:queue-url queue}))

(defn send-message* [queue & [attrs]]
  (sqs!! :send-message
         {:message-body "Hello"
          :queue-url queue
          :attrs attrs}))
