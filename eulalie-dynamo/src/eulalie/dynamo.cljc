(ns eulalie.dynamo
  (:require [eulalie.request]
            [eulalie.service :as service]
            [eulalie.service.generic.json :as generic.json]
            [eulalie.dynamo.request :refer [req->spec]]
            [eulalie.dynamo.impl.key-types :as key-types]
            [eulalie.service.impl.json.mapping :as json.mapping]
            [#?(:clj clojure.spec :cljs cljs.spec) :as s]
            [promesa.core :as p]))

(derive :eulalie/dynamo :eulalie.service.generic.json/request)
(derive :eulalie/dynamo :eulalie.service.generic.json/response)

(defmethod eulalie.request/service->spec :eulalie/dynamo [_]
  (s/keys :req [:eulalie.dynamo.request/body]))

(defmethod service/defaults :eulalie/dynamo [_]
  {:eulalie.sign/service        "dynamodb"
   :eulalie.json/target-prefix  "DynamoDB_20120810."
   :eulalie.request/max-retries 10})

(let [scale-factor   25
      max-backoff-ms (* 20 1000)
      max-retries    9]
  (defn backoff [retries]
    (cond (< retries 0)           (p/resolved nil)
          (< max-retries retries) (p/delay max-backoff-ms)
          :else (-> 1
                    (bit-shift-left retries)
                    (* scale-factor)
                    (min max-backoff-ms)
                    p/delay))))

(defmethod service/request-backoff :eulalie/dynamo [req error]
  (backoff (req :eulalie.request/retries)))

(defmethod generic.json/map-request-keys :eulalie/dynamo [req]
  (json.mapping/transform-request (req :eulalie.dynamo.request/body) key-types/request-key-types))

(defmethod generic.json/map-response-keys :eulalie/dynamo [req]
  (json.mapping/transform-response
   (req :eulalie.response/body)
   key-types/response-key-types))
