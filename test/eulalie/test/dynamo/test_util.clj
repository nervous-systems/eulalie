(ns eulalie.dynamo.test-util
  (:require [eulalie]
            [eulalie.test-util :refer [creds]]
            [cemerick.url :refer [url]]
            [eulalie.util :refer [go-catching <? <?!]]))

(def local-dynamo-url (some-> (System/getenv) (get "LOCAL_DYNAMO_URL") url))

(defn issue [target content & [req-overrides]]
  (let [req (merge
             {:service :dynamo
              :target  target
              :max-retries 0
              :body content
              :creds creds}
             req-overrides)]
    (go-catching
      (let [{:keys [error] :as resp} (<? (eulalie/issue-request! req))]
        (if (not-empty error)
          (throw (ex-info (pr-str error) error))
          resp)))))

(defn issue* [target content & [req-overrides]]
  (-> (issue target content req-overrides) <?! :body))

(defn await-status!! [table status]
  (go-catching
    (loop []
      (let [status' (-> (issue* :describe-table {:table-name table})
                        :table
                        :table-status)]

        (cond (nil? status')     nil
              (= status status') status'
              :else (do
                      (Thread/sleep 1000)
                      (recur)))))))

(def stream-table :eulalie-test-stream-table)
(def stream-table-create
  {:table-name stream-table
   :attribute-definitions
   [(attr [:name :S])]
   :key-schema
   [(key-schema [:name :hash])]
   :provisioned-throughput
   (throughput [1 1])
   :stream-specification
   {:stream-enabled true
    :stream-view-type :new-and-old-images}})

(defn issue-local* [target content & [overrides]]
  (issue* target content (merge {:endpoint local-dynamo-url} overrides)))

(defn with-local-dynamo [f]
  (when local-dynamo-url
    (try
      (issue-local* :delete-table {:table-name stream-table})
      (issue-local* :create-table stream-table-create)
      (catch Exception _))
    (f)))
