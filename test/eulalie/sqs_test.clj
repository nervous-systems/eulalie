(ns eulalie.sqs-test
  (:require [eulalie]
            [eulalie.sqs :refer :all]
            [clojure.test :refer :all]
            [eulalie.test-util :as test-util]))

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

(deftest get-queue-attributes+
  (with-transient-queue
    (fn [{q :url}]
      (let [attrs (sqs!! :get-queue-attributes
                         {:queue-url q
                          :attrs [:maximum-message-size
                                  :last-modified-timestamp]})]
        (is (:maximum-message-size attrs))
        (is (:last-modified-timestamp attrs))))))

(deftest set-queue-attributes+
  (with-transient-queue
    (fn [{q :url}]
      (sqs!! :set-queue-attributes
             {:queue-url q
              :name :delay-seconds
              :value 2}))))

(deftest set-queue-attributes+json
  (with-transient-queue
    (fn [{q :url}]
      (with-transient-queue
        (fn [{redrive-q :url}]
          (let [{:keys [queue-arn]}
                (sqs!! :get-queue-attributes {:queue-url redrive-q :attrs :all})
                r-policy {:max-receive-count 1
                          :dead-letter-target-arn queue-arn}]
            (sqs!! :set-queue-attributes
                   {:queue-url q :name :redrive-policy :value r-policy})
            (let [{:keys [redrive-policy]}
                  (sqs!! :get-queue-attributes {:queue-url q :attrs :all})]
              (is (= r-policy redrive-policy)))))))))

(defn send-message* [queue & [attrs]]
  (sqs!! :send-message
         {:message-body "Hello"
          :queue-url queue
          :attrs attrs}))

(deftest send-message+
  (with-transient-queue
    (fn [{q-url :url}]
      (is (:id (send-message* q-url))))))

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
      (let [attrs {:attributeOne [:number 71]
                   :different    [:string "fourteen"]}
            {m-id :id} (send-message* q-url attrs)
            [msg] (sqs!! :receive-message
                         {:queue-url q-url
                          :wait-time-seconds 2
                          :attrs [:different :attr*]})]
        (is (= {:attributeOne [:number "71"]
                :different   [:string "fourteen"]}
               (:attrs msg)))))))

(defn receive-message* [q-url]
  (sqs!! :receive-message {:queue-url q-url :wait-time-seconds 2}))

(deftest delete-message-batch+
  (with-transient-queue
    (fn [{q-url :url}]
      (let [m-id (send-message* q-url)
            [{:keys [receipt]}] (receive-message* q-url)
            {:keys [succeeded]}
            (sqs!! :delete-message-batch
                   {:queue-url q-url
                    :messages [{:id "0" :receipt-handle receipt}]})]
        (is (succeeded "0"))))))

(deftest delete-message-batch+error
  (with-transient-queue
    (fn [{q-url :url}]
      (let [{:keys [failed]}
            (sqs!! :delete-message-batch
                   {:queue-url q-url
                    :messages [{:id "0" :receipt-handle "garbage"}]})]
        (is (failed "0"))))))

(deftest send-message-batch+
  (with-transient-queue
    (fn [{q-url :url}]
      (let [attrs  {:attribute-1 [:number 71]}
            {:keys [succeeded]}
            (sqs!! :send-message-batch
                   {:queue-url q-url
                    :messages [{:id "0" :attrs attrs :message-body "Hello!"}]})]
        (is (succeeded "0"))))))

(deftest change-message-visibility+
  (with-transient-queue
    (fn [{q-url :url}]
      (send-message* q-url)
      (let [[{:keys [receipt]}] (receive-message* q-url)]
        (is (sqs!! :change-message-visibility
                   {:receipt-handle receipt
                    :queue-url q-url
                    :visibility-timeout 60}))))))

(deftest change-message-visibility-batch+
  (with-transient-queue
    (fn [{q-url :url}]
      (send-message* q-url)
      (let [[{:keys [receipt]}] (receive-message* q-url)
            {:keys [succeeded]}
            (sqs!! :change-message-visibility-batch
                   {:queue-url q-url
                    :messages
                    [{:id "0"
                      :receipt-handle receipt
                      :visibility-timeout 60}]})]
        (is (succeeded "0"))))))
