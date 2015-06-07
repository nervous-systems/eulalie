(ns eulalie.sqs-test
  (:require [eulalie]
            [eulalie.sqs :refer :all]
            [clojure.test :refer :all]
            [eulalie.test-util :as test-util]))

(def sqs!! (test-util/make-issuer :sqs))

(def queue-name "the-best-queue")

(defn get-queue-url* [& [q-name]]
  (sqs!! :get-queue-url {:queue-name (or q-name queue-name)}))

(defn create-queue* [& [q-name]]
  (try
    (sqs!! :create-queue
           {:attrs {:maximum-message-size (* 128 1024)}
            :queue-name (or q-name queue-name)})
    (catch clojure.lang.ExceptionInfo e
      (if (-> e ex-data :type (= :queue-already-exists))
        (get-queue-url* q-name)
        (throw e)))))

(defn purge-queue*  [queue] (sqs!! :purge-queue  {:queue-url queue}))
(defn delete-queue* [queue] (sqs!! :delete-queue {:queue-url queue}))

(deftest create-queue+
  (is (= "http" (-> (create-queue*) (subs 0 4)))))

(deftest get-queue-attributes+
  (let [q (create-queue*)]
    (is (:maximum-message-size
         (sqs!! :get-queue-attributes {:queue-url q
                                       :attrs [:maximum-message-size]})))))

(defn send-message* [& [queue attrs]]
  (sqs!! :send-message
         {:message-body "Hello"
          :queue-url (or queue (create-queue*))
          :attrs attrs}))

(deftest send-message+
  (is (:id (send-message*))))

(defn with-transient-queue [f]
  (let [q-name (str "eulalie-transient-" (gensym))
        q-url  (create-queue* q-name)]
    (try
      (f {:name q-name :url q-url})
      (finally
        (delete-queue* q-url)))))

(deftest purge-queue+
  (with-transient-queue
    (fn [{:keys [url]}]
      (is (purge-queue* url)))))

(deftest receive-message+
  (with-transient-queue
    (fn [{q-url :url}]
      (let [{m-id :id} (send-message* q-url)
            ids (->> (sqs!! :receive-message
                            {:queue-url q-url :wait-time-seconds 2})
                     (map :id))]
        (is (= [m-id] ids))))))

(deftest receive-message+meta
  (with-transient-queue
    (fn [{q-url :url}]
      (let [{m-id :id} (send-message* q-url)
            [msg] (sqs!! :receive-message
                         {:queue-url q-url
                          :wait-time-seconds 2
                          :meta [:sender-id]})]
        (is (:sender-id msg))))))

(deftest receive-message+attrs
  (with-transient-queue
    (fn [{q-url :url}]
      (let [attrs {:attribute-1 [:number 71]
                   :different   [:string "fourteen"]}
            {m-id :id} (send-message* q-url attrs)
            [msg] (sqs!! :receive-message
                         {:queue-url q-url
                          :wait-time-seconds 2
                          :attrs [:different :attr*]})]
        (is (= {:attribute-1 [:number "71"]
                :different   [:string "fourteen"]}
               (:attrs msg)))))))
