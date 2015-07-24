(ns eulalie.test.dynamo-streams
  (:require
   [eulalie.core :as eulalie]
   [eulalie.dynamo-streams]
   [eulalie.test.dynamo.common :as dynamo.common :refer [with-local-dynamo!]]
   [glossop.core #? (:clj :refer :cljs :refer-macros) [go-catching <?]]
   [eulalie.test.common :as test.common
    #? (:clj :refer :cljs :refer-macros) [deftest is]]))

(defn issue! [creds target content & [req-overrides]]
  (go-catching
    (let [req (merge
               {:service :dynamo-streams
                :target  target
                :max-retries 0
                :body content
                :creds creds}
               req-overrides)]
      (:body (<? (test.common/issue-raw! req))))))

(defn with-streams! [f]
  (with-local-dynamo! []
    (fn [creds]
      (go-catching
        (<? (f creds (-> (issue! creds :list-streams {}) <? :streams)))))))

(deftest ^:integration list-streams
  (with-streams!
    (fn [creds streams]
      (go-catching
        (is (some :stream-arn streams))))))

(deftest ^:integration describe-stream
  (with-streams!
    (fn [creds [{:keys [stream-arn]}]]
      (go-catching
        (is (:stream-description
             (<? (issue! creds :describe-stream {:stream-arn stream-arn}))))))))

(defn get-shard-iterator! [creds stream-arn]
  (go-catching
    (let [{{[{:keys [shard-id]}] :shards} :stream-description}
          (<? (issue! creds :describe-stream {:stream-arn stream-arn}))]
      (:shard-iterator
       (<? (issue! creds :get-shard-iterator
                   {:shard-iterator-type :trim-horizon
                    :shard-id shard-id
                    :stream-arn stream-arn}))))))

(deftest ^:integration get-shard-iterator
  (with-streams!
    (fn [creds [{:keys [stream-arn]}]]
      (go-catching
        (is (<? (get-shard-iterator! creds stream-arn)))))))
