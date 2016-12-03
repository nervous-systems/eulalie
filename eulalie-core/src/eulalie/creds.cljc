(ns eulalie.creds
  (:require [eulalie.impl.platform :as platform]
            [eulalie.impl.util :refer [assoc-when]]
            [eulalie.impl.platform.time :as platform.time]
            [eulalie.instance-data :as instance-data]
            [promesa.core :as p])
  (:refer-clojure :exclude [resolve]))

(defn env []
  (let [secret-key (or (platform/env :AWS_SECRET_KEY)
                       (platform/env :AWS_SECRET_ACCESS_KEY))]
    (when (not-empty secret-key)
      (assoc-when
       {:access-key (or (platform/env :AWS_ACCESS_KEY)
                        (platform/env :AWS_ACCESS_KEY_ID))
        :secret-key secret-key}
       :token (platform/env :AWS_SESSION_TOKEN)))))

(defprotocol Credentials
  (refresh! [this])
  (expired? [this])
  (resolve  [this]))

(extend-type #?(:clj  clojure.lang.PersistentArrayMap
                :cljs cljs.core.PersistentArrayMap)
  Credentials
  (refresh! [this]
    (p/resolved this))
  (expired? [this]
    false)
  (resolve [this]
    this))

(defrecord ExpiringCredentials [threshold creds refresh!]
  Credentials
  (expired? [_]
    (let [exp (@creds :expiration)]
      (or (not exp) (<= (- exp (platform.time/msecs-now)) threshold))))
  (refresh! [_]
    (p/then (refresh!)
      (partial reset! creds)))
  (resolve [_]
    @creds))

(def ^:private INSTANCE-CREDS-THRESHOLD (* 60 1000 5))

(defn expiring [threshold refresh!]
  (->ExpiringCredentials threshold (atom {}) refresh!))

(defn instance
  [& [{:keys [role threshold] :or {threshold INSTANCE-CREDS-THRESHOLD}}]]
  (let [refresh! #(instance-data/iam-credentials! role)]
    (expiring threshold refresh!)))
