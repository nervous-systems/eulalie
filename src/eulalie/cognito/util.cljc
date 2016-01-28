(ns eulalie.cognito.util
  (:require [eulalie.core :as eulalie]
            [eulalie.cognito]
            [eulalie.support]
            [glossop.core :as g
             #? (:clj :refer :cljs :refer-macros) [go-catching <?]]))

(defn issue! [creds target body & [{:keys [chan close?] :or {close? true}}]]
  (eulalie.support/issue-request!
   {:service :cognito
    :creds   creds
    :target  target
    :chan    chan
    :close?  close?
    :body    body}))

(defn get-open-id-token-for-developer-identity!
  [creds identity-pool-id logins & [params req-args]]
  (issue! creds
          :get-open-id-token-for-developer-identity
          (merge params {:identity-pool-id identity-pool-id
                         :logins logins})
          req-args))
