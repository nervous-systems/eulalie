(ns eulalie.service
  (:require [eulalie.sign :as sign]
            [eulalie.impl.service :refer [throttling-error?]]
            [promesa.core :as p]))

(let [scale-ms             300
      throttle-scale-ms    500
      throttle-scale-range (/ throttle-scale-ms 4)
      max-backoff-ms       (* 20 1000)]

  (defn default-retry-backoff [retries {:keys [type]}]
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

(defn req->service-dispatch [{:keys [service]}]
  (keyword "eulalie.service" (name service)))

(defn resp->service-dispatch [{req :request}]
  (req->service-dispatch req))

(defmulti prepare-request   req->service-dispatch)
(defmulti transform-request-body req->service-dispatch)

(defmulti transform-response-body  resp->service-dispatch)
(defmulti transform-response-error resp->service-dispatch)

(defmulti  request-backoff (fn [req retries error] (req->service-dispatch req)))
(defmethod request-backoff :default [_ retries error]
  (default-retry-backoff retries error))

(defmulti  sign-request req->service-dispatch)
(defmethod sign-request :default [req]
  ;; After prepare-request, we expect there to be a canonical service name on
  ;; the request, under :eulalie.sign/service e.g. "dynamodb", rather than :dynamo
  (sign/aws4 req))
