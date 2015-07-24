(ns eulalie.lambda.util
  (:require [eulalie.core :as eulalie]
            [eulalie.lambda]
            [glossop.core :as g
             #? (:clj :refer :cljs :refer-macros) [go-catching <?]]))

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
     (def thunk!!   (comp g/<?! thunk!))
     (def invoke!!  (comp g/<?! invoke!))
     (def request!! (comp g/<?! request!))
     (def get-function!! (comp g/<?! get-function!))
     (def add-permission!! (comp g/<?! add-permission!))))
