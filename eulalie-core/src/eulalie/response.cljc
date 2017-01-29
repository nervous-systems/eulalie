(ns ^:no-doc eulalie.response
  (:require [#?(:clj clojure.spec :cljs cljs.spec) :as s]
            [eulalie.request]
            [eulalie.creds]))

(s/def ::status int?)

(s/def :eulalie.error/type        keyword?)
(s/def :eulalie.error/message     string?)
(s/def :eulalie.error/time-offset int?)

(s/def :eulalie/error
  (s/keys :req [:eulalie.error/type :eulalie/request]
          :opt [:eulalie.response/status
                :eulalie.error/message
                :eulalie.error/time-offset]))

(s/def ::body any?)
(s/def :eulalie.response/error :eulalie/error)
(s/def ::headers :eulalie.request/headers)

(s/def :eulalie/response
  (s/keys :req [:eulalie/request (or :eulalie.response/error ::body) ::status (or :eulalie.response/error ::headers)]))
