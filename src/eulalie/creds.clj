(ns eulalie.creds
  (:require [clojure.core.async :as async :refer [>!]]
            [clojure.tools.logging :as log]
            [clojure.set :as set]
            [eulalie.util :refer [<? go-catching]]
            [eulalie.instance-data :as instance-data]
            [clj-time.format :as time.format]
            [clj-time.coerce :as time.coerce]
            [clj-time.core :as time]))

(defmulti  creds->credentials
  "Unfortunately-named mechanism to turn the informally-specified 'creds' map
  supplied by the user into a map with :access-key, :secret-key, :token members,
  suitable for signing requests, etc.  To support more exotic use-cases, like
  mutable/refreshable credentials, we offer this layer of indirection."
  :eulalie/type)
(defmethod creds->credentials :default [creds] creds)
;; Refreshable creds look like:
;;  {:eulalie/type :refresh :current (atom {:token ...))}
(defmethod creds->credentials :refresh [{:keys [current]}] @current)

(let [seconds-formatter (time.format/formatters :date-time-no-ms)]
  (defn from-iso-seconds [x]
    (time.coerce/to-long (time.format/parse seconds-formatter x))))

(defn tidy-iam-creds [m]
  (-> m
      (set/rename-keys {:access-key-id :access-key
                        :secret-access-key :secret-key})
      (update-in [:expiration] from-iso-seconds)))

(defn credential-channel!
  "Writes to the output channel a sequence of instance-specific IAM credentials
  retrieved from retrieval-fn, scheduling the next invocation just prior to
  expiry.  On error, the exception will be written to the output channel before
  closing.  Closing the output channel will terminate early."
  [retrieval-fn & [{:keys [out-chan]}]]
  (let [out-chan (or out-chan (async/chan))
        loop-chan
        (go-catching
          (loop []
            (let [{:keys [expiration] :as creds}
                  (-> (retrieval-fn) <? tidy-iam-creds)
                  now (System/currentTimeMillis)]
              (if (< expiration now)
                (throw (ex-info "expired-credentials"
                                {:type :expired-credentials :now now :creds creds}))
                (when (>! out-chan creds)
                  (log/info (pr-str
                             {:event :scheduled-credential-fetch
                              :data {:expiration expiration}}))
                  (<? (async/timeout (- expiration now (* 60 1000 5))))
                  (recur))))))]
    (async/pipe loop-chan out-chan)
    out-chan))

(defn periodically-refresh!
  ([creds-atom role]
   (let [creds (credential-channel! #(instance-data/iam-credentials! role))]
     (go-catching
       (loop []
         (when-let [current-creds (<? creds)]
           (log/info (pr-str
                      {:event :credential-reset
                       :data {:creds current-creds}}))
           (reset! creds-atom current-creds)
           (recur))))
     creds)))
