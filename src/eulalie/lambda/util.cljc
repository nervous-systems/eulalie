(ns eulalie.lambda.util
  (:require [eulalie.core :as eulalie]
            [eulalie.lambda]
            #?@(:clj
                [[glossop.core :refer [go-catching <? <?!]]]
                :cljs
                [[cljs.core.async]]))
  #?(:cljs
     (:require-macros [glossop.macros :refer [go-catching <?]])))

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


(defn invoke! [creds fn-name type payload & [params]]
  (thunk! creds fn-name type (assoc params :payload payload)))

(defn request! [creds fn-name & [payload]]
  (invoke! creds fn-name :request-response payload))

#?(:clj
   (do
     (def thunk!!   (comp <?! thunk!))
     (def invoke!!  (comp <?! invoke!))
     (def request!! (comp <?! request!))))
