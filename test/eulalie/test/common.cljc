(ns eulalie.test.common
  (:require [eulalie.core :as eulalie]
            [eulalie.util :refer [env!]]
            #? (:clj
                [glossop.core :refer [<? go-catching]]))
  #? (:cljs
      (:require-macros [glossop.macros :refer [<? go-catching]])))

(def gcm-api-key (env! "GCM_API_KEY"))

(def creds
  {:access-key (env! "AWS_ACCESS_KEY")
   :secret-key (env! "AWS_SECRET_KEY")})

(defn with-aws [f]
  (if (not-empty (:secret-key creds))
    (f creds)
    (println "Warning: Skipping test due to empty AWS_SECRET_KEY env var")))

(defn issue-raw! [req]
  (go-catching
    (let [{:keys [error] :as resp} (<? (eulalie/issue-request! req))]
      (if (not-empty error)
        ;; ex-info doesn't print to anything useful in cljs
        (throw #? (:clj
                   (ex-info  (pr-str error) error)
                   :cljs
                   (js/Error (pr-str error))))
        resp))))

(def aws-account-id "510355070671")

