(ns eulalie.creds
  (:require [eulalie.util :as util]
            [eulalie.instance-data :as instance-data]
            [eulalie.platform.time :as platform.time]
            #?@(:clj
                [[glossop.core :refer [<? go-catching]]
                 [clojure.core.async :as async :refer [>! <!]]]
                :cljs
                [[cljs.core.async :as async :refer [>! <!]]]))
  #?(:cljs
     (:require-macros [glossop.macros :refer [go-catching <?]])))

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

(defn- creds-timeout-chan [expiration now]
  (async/timeout (- expiration now (* 60 1000 5))))

(defn credential-channel!
  "Writes to the output channel a sequence of instance-specific IAM credentials
  retrieved from retrieval-fn, scheduling the next invocation just prior to
  expiry.  On error, the exception will be written to the output channel before
  closing.  Closing the output channel will terminate early."
  [{:keys [expiration] :as initial-creds} retrieval-fn & [{:keys [out-chan]}]]
  (let [out-chan (or out-chan (async/chan))
        now      (platform.time/msecs-now)
        loop-chan
        (go-catching
          (when expiration
            (<! (creds-timeout-chan expiration now)))
          (loop []
            (let [{:keys [expiration] :as creds} (<? (retrieval-fn))
                  now (platform.time/msecs-now)]
              (if (< expiration now)
                (throw (ex-info "expired-credentials"
                                {:type :expired-credentials :now now :creds creds}))
                (when (>! out-chan creds)
                  (<! (creds-timeout-chan expiration now))
                  (recur))))))]
    (async/pipe loop-chan out-chan)
    out-chan))

(defn periodically-refresh!
  ([creds-atom role]
   (let [creds (credential-channel!
                @creds-atom #(instance-data/iam-credentials! role))]
     (go-catching
       (loop []
         (when-let [current-creds (<? creds)]
           (reset! creds-atom current-creds)
           (recur))))
     creds)))
