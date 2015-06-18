(ns eulalie.dynamo-streams-test
  (:require
   [eulalie] :reload
   [eulalie.dynamo-streams] :reload
   [eulalie.dynamo.test-util :refer :all]
   [clojure.test :refer :all]))

(defn issue-streams* [target & [body]]
  (issue-local* target (or body {}) {:service :dynamo-streams}))

(deftest list-streams+
  (with-local-dynamo
    #(let [{:keys [stream-ids last-evaluated-stream-id]}
           (issue-streams* :list-streams
                           {:table-name stream-table
                            :limit 1})]
       (is stream-ids)
       (is last-evaluated-stream-id))))
