(ns eulalie.test-util
  (:require [eulalie]))

(def creds
  {:access-key (get (System/getenv) "AWS_ACCESS_KEY")
   :secret-key (get (System/getenv) "AWS_SECRET_KEY")})

(def gcm-api-key (get (System/getenv) "GCM_API_KEY"))

;; XXX make this an env var
(def aws-account-id "510355070671")

(defn make-issuer [service]
  (fn [target body]
    (let [{:keys [error body]}
          (eulalie/issue-request!!
           {:service service
            :creds   creds
            :target  target
            :body    body})]
      (if error
        (throw (ex-info (name (:type error)) error))
        body))))
