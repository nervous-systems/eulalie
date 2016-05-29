(ns eulalie.test.dynamo.common
  (:require
   [eulalie.core :as eulalie]
   [glossop.core #? (:clj :refer :cljs :refer-macros) [go-catching <?]]
   [eulalie.dynamo]
   [eulalie.util :as util :refer [env!]]
   [plumbing.core :refer [map-vals]]
   [eulalie.test.common :as test.common :refer [creds]]))

(def attr       (partial zipmap [:attribute-name :attribute-type]))
(def throughput (partial zipmap [:read-capacity-units :write-capacity-units]))
(def key-schema (partial zipmap [:attribute-name :key-type]))

(def table :eulalie-test-table)

(def local-dynamo-url (env! "LOCAL_DYNAMO_URL"))

(defn issue! [creds target content & [req-overrides]]
  (go-catching
    (let [req (merge {:service     :dynamo
                      :target      target
                      :max-retries 0
                      :body        content
                      :creds       creds} req-overrides)]
      (:body (<? (test.common/issue-raw! req))))))

(def batch-write! #(issue! %1 :batch-write-item {:request-items %2}))
(defn batch-get! [creds m]
  (go-catching
    (-> (issue! creds :batch-get-item {:request-items m})
        <?
        (update-in [:responses] #(map-vals set %)))))

(def create-table-req
  {:table-name table
   :stream-specification {:stream-enabled true
                          :stream-view-type :new-and-old-images}
   :attribute-definitions
   [(attr [:name :S]) (attr [:age :N]) (attr [:job :S])]
   :key-schema
   [(key-schema [:name :hash]) (key-schema [:age :range])]
   :global-secondary-indexes
   [{:index-name :amazing-index
     :key-schema [(key-schema [:age :hash])]
     :projection
     {:non-key-attributes [:job]
      :projection-type :include}
     :provisioned-throughput (throughput [2 2])}]
   :local-secondary-indexes
   [{:index-name :local-index
     :key-schema [(key-schema [:name :hash])
                  (key-schema [:job  :range])]
     :projection
     {:projection-type :all}}]
   :provisioned-throughput
   (throughput [1 1])})

(defn clean-items! [creds table->key-sets]
  (batch-write!
   creds
   (into {}
     (for [[t key-sets] table->key-sets]
       [table (for [ks key-sets] {:delete-request {:key ks}})]))))

(defn with-items! [creds items f]
  (go-catching
    (<? (batch-write!
         creds
         {table (for [item items] {:put-request {:item item}})}))

    (let [table-keys
          (->> create-table-req :key-schema (map :attribute-name) (into #{}))]
      (try
        (<? (f creds))
        (finally
          (clean-items!
           creds
           {table (for [item items] (select-keys item table-keys))}))))))

(defn with-local-dynamo!
  ([items f]
   (go-catching
     (if-let [url (not-empty local-dynamo-url)]
       (let [creds (assoc creds :endpoint url)]
         (try
           (<? (issue! creds :delete-table {:table-name table}))
           (catch #? (:clj Exception :cljs js/Error) e
             (when (not= :resource-not-found-exception (-> e ex-data :type))
               (throw e))))
         (<? (issue! creds :create-table create-table-req))
         (if (not-empty items)
           (<? (with-items! creds items f))
           (<? (f creds))))
       (println "Warning: Skipping local test due to unset LOCAL_DYNAMO_URL")))))

;; For now, this just assumes the table exists - otherwise it's super slow

(defn with-remote-dynamo!
  ([items f]
   (go-catching
     (if (not-empty (:secret-key creds))
       (if (not-empty items)
         (<? (with-items! creds items f))
         (when-let [resp (f creds)]
           (<? resp)))
       (println "Warning: Skipping remote test due to unset AWS_SECRET_KEY")))))
