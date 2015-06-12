(ns eulalie.sqs-test
  (:require [eulalie]
            [eulalie.sqs]
            [eulalie.sqs.test-util :refer :all]
            [clojure.test :refer :all]
            [eulalie.test-util :as test-util]))

(deftest get-queue-attributes+
  (with-transient-queue
    (fn [{q :url}]
      (let [attrs (sqs!! :get-queue-attributes
                         {:queue-url q
                          :attrs [:maximum-message-size
                                  :last-modified-timestamp]})]
        (is (:maximum-message-size attrs))
        (is (:last-modified-timestamp attrs))))))

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
                :different    [:string "fourteen"]}
               (:attrs msg)))))))

(defn receive-message* [q-url]
  (sqs!! :receive-message {:queue-url q-url :wait-time-seconds 2}))

(deftest delete-message-batch+
  (with-transient-queue
    (fn [{q-url :url}]
      (let [m-id (send-message* q-url)
            [{:keys [receipt-handle]}] (receive-message* q-url)
            {:keys [succeeded]}
            (sqs!! :delete-message-batch
                   {:queue-url q-url
                    :messages [{:id "0" :receipt-handle receipt-handle}]})]
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
            {{msg "0"} :succeeded}
            (sqs!! :send-message-batch
                   {:queue-url q-url
                    :messages [{:id "0" :attrs attrs :message-body "Hello!"}]})]
        (is ((every-pred :body-md5 :attr-md5 :id) msg))))))

(deftest change-message-visibility+
  (with-transient-queue
    (fn [{q-url :url}]
      (send-message* q-url)
      (let [[{:keys [receipt-handle]}] (receive-message* q-url)]
        (is (sqs!! :change-message-visibility
                   {:receipt-handle receipt-handle
                    :queue-url q-url
                    :visibility-timeout 60}))))))

(deftest change-message-visibility-batch+
  (with-transient-queue
    (fn [{q-url :url}]
      (send-message* q-url)
      (let [[{:keys [receipt-handle]}] (receive-message* q-url)
            {:keys [succeeded]}
            (sqs!! :change-message-visibility-batch
                   {:queue-url q-url
                    :messages
                    [{:id "0"
                      :receipt-handle receipt-handle
                      :visibility-timeout 60}]})]
        (is (succeeded "0"))))))
