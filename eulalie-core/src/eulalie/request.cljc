(ns ^:no-doc eulalie.request
  (:require [#?(:clj clojure.spec :cljs cljs.spec) :as s]
            [#?(:clj clojure.spec.gen :cljs cljs.spec.impl.gen) :as gen]
            [eulalie.creds]))

(s/def ::retries     int?)
(s/def ::target      keyword?)
(s/def ::max-retries int?)
(s/def ::creds       :eulalie/creds)
(s/def ::service     keyword?)
(s/def ::region      keyword?)
(s/def ::endpoint    (s/with-gen (some-fn string? map?) gen/string))
(s/def ::method      keyword?)
(s/def ::date        number?)

(s/def ::base
  (s/keys :req [::service ::target]
          :opt [::creds ::retries ::max-retries ::region ::endpoint ::method ::date]))

(s/def ::headers (s/map-of keyword? any?))

(s/def :eulalie.sign/service string?)
(s/def :eulalie.sign/creds   :eulalie.creds/map)

(s/def ::prepared
  (s/keys :req [::service :eulalie.sign/service ::target ::max-retries ::method
                ::endpoint ::headers]
          :opt [::date]))

(s/def ::signable
  (-> (s/keys :req [:eulalie.sign/creds ::region])
      (s/merge ::prepared)))

(s/def ::authorization string?)
(s/def ::x-amz-content-sha256 string?)

(s/def :eulalie.request.signed/headers
  (s/or :aws4 (s/keys :req [::authorization ::x-amz-content-sha256])))

(s/def ::signed
  (-> (s/keys :req [:eulalie.request.signed/headers])
      (s/merge ::signable)))

(defmulti service->spec ::service)

(s/def :eulalie/request
  (-> (s/multi-spec service->spec ::service)
      (s/merge ::base)))
