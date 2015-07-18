(ns eulalie.lambda.util
  (:require [eulalie.core :as eulalie]
            [eulalie.lambda]
            #?@(:clj
                [[glossop.core :refer [go-catching <? <?!]]]
                :cljs
                [[cljs.core.async]]))
  #?(:cljs
     (:require-macros [glossop.macros :refer [go-catching <?]])))

(defn issue! [creds target body]
  (go-catching
    (let [{:keys [body error]}
          (<? (eulalie/issue-request!
               {:service :lambda
                :creds creds
                :target target
                :body body}))]
      (if error
        (ex-info (name (:type error)) error)
        body))))

(defn thunk! [creds fn-name type & [params]]
  (issue!
   creds
   :invoke
   (assoc params
          :function-name fn-name
          :invocation-type type)))


(defn invoke! [creds fn-name type payload & [params]]
  (thunk! creds fn-name type (assoc params :payload payload)))

(defn request! [creds fn-name & [payload]]
  (invoke! creds fn-name :request-response payload))

(defn get-function! [creds fn-name]
  (issue! creds :get-function {:function-name fn-name}))

(defn add-permission! [creds fn-name perm]
  (issue! creds :add-permission (assoc perm :function-name fn-name)))

#?(:clj
   (do
     (def thunk!!   (comp <?! thunk!))
     (def invoke!!  (comp <?! invoke!))
     (def request!! (comp <?! request!))
     (def get-function!! (comp <?! get-function!))
     (def add-permission!! (comp <?! add-permission!))))
