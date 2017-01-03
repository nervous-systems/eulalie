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
            [eulalie.impl.http :as http]
            [eulalie.impl.service :refer [throttling-error?]]
            [promesa.core :as p]))

(let [scale-ms             300
      throttle-scale-ms    500
      throttle-scale-range (/ throttle-scale-ms 4)
      max-backoff-ms       (* 20 1000)]

  (defn default-retry-backoff
    "Default, exponential implementation of [[request-backoff]]."
    [retries {:keys [type]}]
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

(defn- k->service [k]
  (when k
    (if (namespace k)
      k
      (keyword "eulalie.service" (name k)))))

(defn ^:no-doc req->service-dispatch [{:keys [service]}]
  (k->service service))

(defn ^:no-doc resp->service-dispatch [{req :request}]
  (req->service-dispatch req))

(defmulti defaults
  "Given a qualified service name, return map of default values to be merged
  into all user requests.  Result may be cached."
  k->service)
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
(defmethod transform-request-body :default [req] (req :body))

(defmulti transform-response-body
  "Reshape the response's `:body` (as received from the remote service) into
  something comprehensible to the user.  Returns the body only."
  resp->service-dispatch)
(defmethod transform-response-body :default [resp]
  (resp :body))

(defmulti transform-response-error
  "Takes a response map and returns its `:error` map (required to contain a
  `:eulalie.error/type` keyword key), transforming it as necessary."
  resp->service-dispatch)
(defmethod transform-response-error :default [resp]
  (resp :error))

(defmulti  request-backoff
  "Return a promise resolving when an acceptable backoff period for the given
  request has elapsed.  Defaults to [[default-retry-backoff]]."
  (fn [request retries error] (req->service-dispatch request)))
(defmethod request-backoff :default [_ retries error]
  (default-retry-backoff retries error))

(defmulti  sign-request
  "Sign the given request with the signature algorithm associated with the
  remote service.  Defaults to [[eulalie.sign/aws4]]."
  req->service-dispatch)
(defmethod sign-request :default [req]
  ;; After prepare-request, we expect there to be a canonical service name on
  ;; the request, under :eulalie.sign/service e.g. "dynamodb", rather than :dynamo
  (sign/aws4 req))

(defmulti ^:no-doc issue-request! req->service-dispatch)
(defmethod issue-request! :default [req]
  (http/request! req))
