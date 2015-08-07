(ns eulalie.lambda.util
  (:require [eulalie.core :as eulalie]
            [eulalie.lambda]
            [eulalie.support]
            [glossop.core :as g
             #? (:clj :refer :cljs :refer-macros) [go-catching <?]]))

(defn issue! [creds target body & [{:keys [chan close?] :or {close? true}}]]
  (eulalie.support/issue-request!
   {:service :lambda
    :creds   creds
    :target  target
    :chan    chan
    :close?  close?
    :body    body}))

(defn thunk! [creds fn-name type & [params req-args]]
  (issue!
   creds
   :invoke
   (assoc params
          :function-name fn-name
          :invocation-type type)
   req-args))

(defn invoke! [creds fn-name type payload & [params req-args]]
  (thunk! creds fn-name type (assoc params :payload payload) req-args))

(defn request! [creds fn-name & [payload params req-args]]
  (invoke! creds fn-name :request-response payload params req-args))

(defn get-function! [creds fn-name & [req-args]]
  (issue! creds :get-function {:function-name fn-name} req-args))

(defn add-permission! [creds fn-name perm & [req-args]]
  (issue! creds :add-permission (assoc perm :function-name fn-name) req-args))

#?(:clj
   (do
     (def thunk!!   (comp g/<?! thunk!))
     (def invoke!!  (comp g/<?! invoke!))
     (def request!! (comp g/<?! request!))
     (def get-function!! (comp g/<?! get-function!))
     (def add-permission!! (comp g/<?! add-permission!))))
