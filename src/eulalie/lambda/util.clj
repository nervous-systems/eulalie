(ns eulalie.lambda.util
  (:require [eulalie]
            [eulalie.lambda]
            [eulalie.util :refer [go-catching <? <?!]]))

(defn thunk! [creds fn-name type & [params]]
  (go-catching
    (let [{:keys [body error]}
          (<? (eulalie/issue-request!
               {:service :lambda
                :creds creds
                :target :invoke
                :body (assoc params
                             :function-name fn-name
                             :invocation-type type)}))]
      (if error
        (ex-info (name (:type error)) error)
        body))))
(def thunk!! (comp <?! thunk!))

(defn invoke! [creds fn-name type payload & [params]]
  (thunk! creds fn-name type (assoc params :payload payload)))
(def invoke!! (comp <?! invoke!))
