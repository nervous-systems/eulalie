(ns eulalie.test.common
  (:require [eulalie.core :as eulalie]
            [eulalie.util :refer [env!]]
            #? (:clj  [clojure.core.async.impl.protocols :as async-protocols])
            [glossop.core :as g
             #? (:clj :refer :cljs :refer-macros) [go-catching <?]]
            #?@ (:clj
                 [[clojure.test :as test]
                  [glossop.core :refer [<?!]]]
                 :cljs
                 [[cljs.test]]))
  #? (:cljs (:require-macros [eulalie.test.common])))

#? (:cljs (set! *main-cli-fn* identity))

#? (:clj
    (defmacro deftest [t-name & forms]
      (if (:ns &env)
        `(cljs.test/deftest ~t-name
           (let [result# (do ~@forms)]
             (when (satisfies? cljs.core.async.impl.protocols/ReadPort result#)
               (cljs.test/async
                done#
                (go-catching
                  (try
                    (<? result#)
                    (catch js/Error e#
                      (cljs.test/is (nil? e#))))
                  (done#))))))
        `(test/deftest ~t-name
           (let [result# (do ~@forms)]
             (when (satisfies? async-protocols/ReadPort result#)
               (<?! result#)))))))

#? (:clj
    (defmacro is [& args]
      (if (:ns &env)
        `(cljs.test/is ~@args)
        `(test/is ~@args))))

(def gcm-api-key    (env! "GCM_API_KEY"))
(def aws-account-id (env! "AWS_ACCOUNT_ID"))

(def creds
  {:access-key (env! "AWS_ACCESS_KEY")
   :secret-key (env! "AWS_SECRET_KEY")})

(defn with-aws [f]
  (if (not-empty (:secret-key creds))
    (f creds)
    ;; Most of the time, the runner is expecting a channel
    (go-catching
      (println "Warning: Skipping test due to empty AWS_SECRET_KEY env var"))))

(defn issue-raw! [req]
  (go-catching
    (let [{:keys [error] :as resp} (<? (eulalie/issue-request! req))]
      (if (not-empty error)
        (throw (ex-info (pr-str error) error))
        resp))))

(defn sts! [creds target content & [req-overrides]]
  (go-catching
    (let [req (merge
               {:service :sts
                :target  target
                :max-retries 0
                :body content
                :creds creds}
               req-overrides)]
      (:body (<? (issue-raw! req))))))
