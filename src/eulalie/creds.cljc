(ns eulalie.creds
  (:require [eulalie.util :as util]
            [eulalie.instance-data :as instance-data]
            [eulalie.platform.time :as platform.time]
            #? (:clj
                [glossop.core :refer [<? go-catching]]))
  #?(:cljs
     (:require-macros [glossop.macros :refer [go-catching <?]])))

(defn env []
  (let [token (util/env! "AWS_SESSION_TOKEN")]
    (cond->
        {:access-key (util/env! "AWS_ACCESS_KEY_ID")
         :secret-key (util/env! "AWS_SECRET_ACCESS_KEY")}
      token (assoc :token token))))

(defmulti creds->credentials
  "Unfortunately-named mechanism to turn the informally-specified 'creds' map
  supplied by the user into a map with :access-key, :secret-key, :token members,
  suitable for signing requests, etc.  To support more exotic use-cases, like
  mutable/refreshable credentials, we offer this layer of indirection."
  :eulalie/type)
(defmethod creds->credentials :default [creds]
  (go-catching creds))

(defmethod creds->credentials :mutable [{:keys [current]}]
  (go-catching @current))

(defn refresh! [{:keys [current refresh] :as creds}]
  (go-catching
    (reset! current (<? (refresh)))
    creds))

(defmethod creds->credentials :expiring
  [{:keys [threshold current refresh] :as m}]
  (go-catching
    (let [{:keys [expiration]} @current]
      (when (or (not expiration)
                (<= (- expiration (platform.time/msecs-now)) threshold))
        ;; So this is pretty wasteful - there could be large numbers of
        ;; concurrent requests, all with the same expired credentials - they
        ;; should all be waiting on a single request
        (<? (refresh! m)))
      m)))

(defn expiring-creds
  [refresh-fn & [{:keys [threshold]
                  :or {threshold (* 60 1000 5)}}]]
  {:eulalie/type :expiring
   :current (atom nil)
   :refresh refresh-fn
   :threshold threshold})

(defn iam
  ([]
   (expiring-creds instance-data/default-iam-credentials!))
  ([role]
   (expiring-creds #(instance-data/iam-credentials! role))))
