(ns eulalie.test.sqs
  (:require [eulalie.core :as eulalie]
            [eulalie.sqs]
            [eulalie.test.sqs.util :refer [sqs!] :as sqs.util]
            [eulalie.test.common :as test.common :refer [creds]]
            #?@ (:clj
                 [[eulalie.test.async :refer [deftest]]
                  [clojure.core.async :as async]
                  [clojure.test :refer [is]]
                  [glossop.core :refer [<? go-catching]]]
                 :cljs
                 [[cemerick.cljs.test]
                  [cljs.core.async :as async]]))
  #? (:cljs (:require-macros [glossop.macros :refer [<? go-catching]]
                             [eulalie.test.async.macros :refer [deftest]]
                             [cemerick.cljs.test :refer [is]])))

(deftest ^:integration ^:aws get-queue-attributes
  (sqs.util/with-transient-queue!
    (fn [{q :url}]
      (go-catching
        (let [attrs (<? (sqs! :get-queue-attributes
                              {:queue-url q
                               :attrs [:maximum-message-size
                                       :last-modified-timestamp]}))]
          (is (:maximum-message-size attrs))
          (is (:last-modified-timestamp attrs)))))))

(deftest ^:integration ^:aws send-message
  (sqs.util/with-transient-queue!
    (fn [{q-url :url}]
      (go-catching
        (is (:id (<? (sqs.util/send-message! q-url))))))))

(deftest ^:integration ^:aws purge-queue
  (sqs.util/with-transient-queue!
    (fn [{:keys [url]}]
      (go-catching
        (is (<? (sqs.util/purge-queue! url)))))))

(deftest ^:integration ^:aws receive-message
  (sqs.util/with-transient-queue!
    (fn [{q-url :url}]
      (go-catching
        (let [{m-id :id} (<? (sqs.util/send-message! q-url))
              ids (->> (sqs! :receive-message
                             {:queue-url q-url :wait-time-seconds 2})
                       <?
                       (map :id))]
          (is (= [m-id] ids)))))))

(deftest ^:integration ^:aws receive-message+meta
  (sqs.util/with-transient-queue!
    (fn [{q-url :url}]
      (go-catching
        (let [{m-id :id} (sqs.util/send-message! q-url)
              [msg] (<? (sqs! :receive-message
                              {:queue-url q-url
                               :wait-time-seconds 2
                               :meta [:sender-id]}))]
          (is (:sender-id (:meta msg))))))))

(deftest ^:integration ^:aws receive-message+attrs
  (sqs.util/with-transient-queue!
    (fn [{q-url :url}]
      (go-catching
        (let [attrs {:attributeOne [:number 71]
                     :different    [:string "fourteen"]}
              {m-id :id} (<? (sqs.util/send-message! q-url attrs))
              [msg] (<? (sqs! :receive-message
                              {:queue-url q-url
                               :wait-time-seconds 2
                               :attrs [:different :attr*]}))]
          (is (= {:attributeOne [:number "71"]
                  :different    [:string "fourteen"]}
                 (:attrs msg))))))))

(defn receive-message! [q-url]
  (sqs! :receive-message {:queue-url q-url :wait-time-seconds 2}))

(deftest ^:integration ^:aws delete-message-batch
  (sqs.util/with-transient-queue!
    (fn [{q-url :url}]
      (go-catching
        (let [m-id (<? (sqs.util/send-message! q-url))
              [{:keys [receipt-handle]}] (<? (receive-message! q-url))
              {:keys [succeeded]}
              (<? (sqs! :delete-message-batch
                        {:queue-url q-url
                         :messages [{:id "0" :receipt-handle receipt-handle}]}))]
          (is (succeeded "0")))))))

(deftest ^:integration ^:aws delete-message-batch+error
  (sqs.util/with-transient-queue!
    (fn [{q-url :url}]
      (go-catching
        (let [{:keys [failed]}
              (<? (sqs! :delete-message-batch
                        {:queue-url q-url
                         :messages [{:id "0" :receipt-handle "garbage"}]}))]
          (is (failed "0")))))))

(deftest ^:integration ^:aws send-message-batch
  (sqs.util/with-transient-queue!
    (fn [{q-url :url}]
      (go-catching
        (let [attrs  {:attribute-1 [:number 71]}
              {{msg "0"} :succeeded}
              (<? (sqs! :send-message-batch
                        {:queue-url q-url
                         :messages [{:id "0" :attrs attrs :message-body "Hello!"}]}))]
          (is ((every-pred :body-md5 :attr-md5 :id) msg)))))))

(deftest ^:integration ^:aws change-message-visibility
  (sqs.util/with-transient-queue!
    (fn [{q-url :url}]
      (go-catching
        (<? (sqs.util/send-message! q-url))
        (let [[{:keys [receipt-handle]}] (<? (receive-message! q-url))]
          (is (sqs! :change-message-visibility
                    {:receipt-handle receipt-handle
                     :queue-url q-url
                     :visibility-timeout 60})))))))

(deftest ^:integration ^:aws change-message-visibility-batch
  (sqs.util/with-transient-queue!
    (fn [{q-url :url}]
      (go-catching
        (<? (sqs.util/send-message! q-url))
        (let [[{:keys [receipt-handle]}] (<? (receive-message! q-url))
              {:keys [succeeded]}
              (<? (sqs! :change-message-visibility-batch
                        {:queue-url q-url
                         :messages
                         [{:id "0"
                           :receipt-handle receipt-handle
                           :visibility-timeout 60}]}))]
          (is (succeeded "0")))))))
