(ns eulalie.service
  "(Likely only of interest if adding support for a new AWS service)

  Services implement multimethods to which signing, body transformation, retry
  backoff, etc. are delegated by [[eulalie.core]].

  Services are identified by namespaced keywords,
  e.g. `:eulalie.service/dynamo`, with keyword hierarchies used to influence the
  default implementation of common service operations.

  The multimethods generally dispatch on the request's `:service` key,
  defaulting the keyword's namespace to `eulalie.service` unless specified.

  Request-oriented methods will receive the entire request, even if responsible
  for transforming only some part of it, as, e.g. headers, arbitrary user keys,
  etc. may be required to the construct the body.  Similarly for responses."
  (:require [eulalie.sign :as sign]
            [eulalie.request]
            [eulalie.response]
            [eulalie.impl.http :as http]
            [eulalie.impl.service :refer [throttling-error?]]
            [promesa.core :as p]
            [#?(:clj clojure.spec :cljs cljs.spec) :as s]))

(let [scale-ms             300
      throttle-scale-ms    500
      throttle-scale-range (/ throttle-scale-ms 4)
      max-backoff-ms       (* 20 1000)]

  (defn default-retry-backoff
    "Default, exponential implementation of [[request-backoff]]."
    [{:keys [eulalie.request/retries]} {:keys [eulalie.error/type]}]
    (if (zero? retries)
      (p/resolved nil)
      (let [scale-factor
            (if (throttling-error? type)
              (+ throttle-scale-ms (rand-int throttle-scale-range))
              scale-ms)]
        (-> 1
            (bit-shift-left retries)
            (* scale-factor)
            (min max-backoff-ms)
            p/delay)))))

(defn ^:no-doc req->service-dispatch [{:keys [eulalie.request/service]}]
  service)

(s/fdef req->service-dispatch
  :args (s/cat :req :eulalie/request))

(defn ^:no-doc resp->service-dispatch [{req :eulalie/request}]
  (req->service-dispatch req))

(s/fdef resp->service-dispatch
  :args (s/cat :req :eulalie/response))

(defmulti defaults
  "Given a qualified service name, return map of default values to be merged
  into all user requests.  Result may be cached."
  identity)
(defmethod defaults :default [_] {})

(defmulti prepare-request
  "Add service-specific headers to the given request."
  req->service-dispatch)
(defmethod prepare-request :default [req] req)

(defmulti transform-request-body
  "Reduce or reshape any high-level representations in the request's `:body` in
  accordance with the expectations of the remote service.  Returns the `:body`
  only."
  req->service-dispatch)
(defmethod transform-request-body :default [req]
  (req :eulalie.request/body))

(defmulti transform-response-body
  "Reshape the response's body (as received from the remote service) into
  something comprehensible to the user.  Returns the body only."
  resp->service-dispatch)
(defmethod transform-response-body :default [resp]
  (resp :eulalie.response/body))

(defmulti transform-response-error
  "Takes a response map and returns its `:eulalie.response/error` map (required
  to contain a `:eulalie.error/type` keyword key), transforming it as
  necessary."
  resp->service-dispatch)
(defmethod transform-response-error :default [resp]
  (resp :eulalie.response/error))

(defn- backoff-dispatch [req error]
  (req :eulalie.request/service))

(s/fdef backoff-dispatch
  :args (s/cat :req :eulalie/request :error :eulalie/error)
  :ret  p/promise?)

(defmulti request-backoff
  "Return a promise resolving when an acceptable backoff period for the given
  request has elapsed.  Defaults to [[default-retry-backoff]]."
  backoff-dispatch)
(defmethod request-backoff :default [req error]
  (default-retry-backoff req error))

(s/def :eulalie.request/retryable
  (-> (s/keys :req [:eulalie.request/retries :eulalie.response/error])
      (s/merge :eulalie.request/signed)))

(defmulti  sign-request
  "Sign the given request with the signature algorithm associated with the
  remote service. Defaults to [[eulalie.sign/aws4]]."
  req->service-dispatch)
(defmethod sign-request :default [req]
  ;; After prepare-request, we expect there to be a canonical service name on
  ;; the request, under :eulalie.sign/service e.g. "dynamodb", rather than :dynamo
  (sign/aws4 req))

(defmulti ^:no-doc issue-request! req->service-dispatch)
(defmethod issue-request! :default [req]
  (http/request! req))
