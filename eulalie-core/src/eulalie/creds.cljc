(ns eulalie.creds
  "Wherever credentials are required, they may be represented either as plain
  maps having keys `:access-key`, `:secret-key`, and optionally `:token` and
  `:expiration`, or [[Credentials]] implementations used for indirection.

  For utility, credentials may carry `:region` (keyword, e.g. `:us-east-1`) and
  `:endpoint`) (`cemerick.url/url`) keys, which must be supplied by the
  user.  [[eulalie.core/issue!]] will observe these values over those supplied
  by the service implementation, however they may be overriden by supplying the
  same keys on individual requests."
  (:require [#?(:clj clojure.spec :cljs cljs.spec) :as s]
            [#?(:clj clojure.spec.gen :cljs cljs.spec.impl.gen) :as gen]
            [eulalie.impl.platform :as platform]
            [eulalie.impl.util :refer [assoc-when]]
            [eulalie.impl.platform.time :as platform.time]
            [eulalie.instance-data :as instance-data]
            [promesa.core :as p])
  (:refer-clojure :exclude [resolve]))

(s/def ::access-key string?)
(s/def ::secret-key string?)
(s/def ::token      (s/nilable string?))
(s/def ::expiration (s/nilable number?))

(s/def :eulalie.creds/map
  (s/keys :req-un [::access-key ::secret-key]
          :opt-un [::token ::expiration]))

(defn env
  "Construct a credentials map from the AWS SDK's conventional environment
  variables - either `AWS_SECRET_KEY` / `AWS_SECRET_ACCESS_KEY`,
  `AWS_ACCESS_KEY` / `AWS_ACCESS_KEY_ID` and optionally `AWS_SESSION_TOKEN`.

  Returns `nil` if the environment doesn't contain credentials, or the runtime
  has no concept of `environment`."
  []
  (when-let [secret-key (not-empty (or (platform/env :AWS_SECRET_KEY)
                                       (platform/env :AWS_SECRET_ACCESS_KEY)))]
    (when-let [access-key (not-empty (or (platform/env :AWS_ACCESS_KEY)
                                         (platform/env :AWS_ACCESS_KEY_ID)))]
      (assoc-when
       {:secret-key secret-key
        :access-key access-key}
       :token (not-empty (platform/env :AWS_SESSION_TOKEN))))))

(s/fdef env :ret ::map)

(defprotocol Credentials
  (refresh! [this] "Return a promise resolving to a credentials map.")
  (expired? [this] "Boolean indicating whether the underlying credentials remain valid.")
  (resolve  [this] "Return the underlying credentials map."))

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

(defn ^:no-doc expiring [threshold refresh!]
  (->ExpiringCredentials threshold (atom {}) refresh!))

(s/def ::credentials
  (s/with-gen #(satisfies? Credentials %)
    #(s/gen ::map)))

(defn instance
  "Immediately return an on-demand [[Credentials]] provider which'll retrieve
  and automatically refresh either the default IAM credentials associated with
  the current EC2 instance, or the instance's credentials for the named IAM
  `:role`."
  [& [{:keys [role threshold] :or {threshold INSTANCE-CREDS-THRESHOLD}}]]
  (let [refresh! #(instance-data/iam-credentials! role)]
    (expiring threshold refresh!)))

(s/fdef instance :ret ::credentials)

(defn default
  "Return either [[env]] credentials, if set, or [[instance]] credentials."
  []
  (or (env) (instance)))

(s/def :eulalie/creds
  (s/or :map ::map :credentials ::credentials))

(s/fdef default :ret :eulalie/creds)
