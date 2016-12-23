(ns eulalie.core
  "Responsible for transforming maps describing remote service operations, and
  issuing them as HTTP requests to AWS.  The majority of this work is done by
  delegating to service-specific functionality (see [[eulalie.service]]).

  Request maps are required to have keys:

  - `:service` (keyword), used for method dispatch in [[eulalie.service]].  The
  namespace containing the method definitions must be loaded, even if not used
  directly.
  - `:creds` (map or [[eulalie.creds/Credentials]] implementation).

  Optional keys used to override the service's defaults:

  - `:max-retries` (number), maximum number of times a request will be replayed
  if the remote service returns a retryable error.
  - `:endpoint` (`cemerick.url/url`) identifies the remote service endpoint,
  Will override both the value specified in `:creds`, if any, and the service's
  default endpoint.
  - `:region` (keyword), overrides both the region specified in `:creds`, if
  any, and the service's default region (likely `:us-east-1`).
  - `:method` (keyword) HTTP method.

  Depending on the specifics of the service implementation, `:target` (keyword)
  may be used to identify a remote operation, and `:body` may be used to
  communicate parameters, however this is only convention."
  (:require [eulalie.service       :as service]
            [eulalie.impl.platform :as platform]
            [eulalie.impl.service  :as service-util]
            [eulalie.creds         :as creds]
            [taoensso.timbre :as log]
            [promesa.core    :as p]
            [kvlt.util :refer [pprint-str]]))

(defn dgb [x]
  (println x)
  x)
(defn- prepare-req [{:keys [endpoint headers] :as req}]
  (let [req  (-> req
                 (service-util/default-request (service/request-defaults (req :service)))
                 dgb
                 service/prepare-request
                 dgb
                 (update :endpoint service-util/concretize-port))
        body (service/transform-request-body req)]
    (-> req
        (assoc :body body)
        (assoc-in [:headers :content-length] (platform/byte-count body)))))

(defn- ok? [{:keys [status]}]
  (and status (<= 200 status 299)))

(defn- parse-error [{:keys [headers body] :as resp}]
  (if (resp ::transport-error?)
    {:status 0 :type :transport}
    (service-util/decorate-error
     (let [e (service-util/headers->error-type headers)]
       (or (service/transform-response-error (assoc resp :error {:type e}))
           {:type (or e :unrecognized)}))
     resp)))

(defn- handle-result
  [{aws-resp :response
    req      :request :as result}]

  (if-not (service-util/response-checksum-ok? aws-resp)
    [:error {:type :crc32-mismatch}]
    (if (ok? aws-resp)
      [:ok (service/transform-response-body aws-resp)]
      (let [error (parse-error aws-resp)]
        (if (and (service-util/retry? (aws-resp :status) error)
                 (< (req ::retries) (req :max-retries)))
          [:retry {:timeout (service/request-backoff req (req ::retries) error)
                   :error   error}]
          [:error error])))))

(defn- issue-retrying! [{:keys [creds] :as req}]
  (p/alet [creds (p/await (if (creds/expired? creds)
                            (creds/refresh! creds)
                            (p/resolved (creds/resolve creds))))
           req   (assoc req :creds creds)
           req'  (service/sign-request req)]
    (log/debug "Issuing\n" (pprint-str req'))
    (p/alet [resp   (p/await (service/issue-request! req'))
             result {:response (-> resp (dissoc :opts) (assoc :request req))
                     :request  req'}
             [label value] (handle-result result)]
      (case label
        :ok    (assoc result :body  value)
        :error
        (let [{:keys [type message]} value]
          (throw (ex-info (or message (name type)) (assoc result :error value))))
        :retry
        (let [{:keys [timeout error]} value]
          (log/debug "Retrytable error" error
                     (str "(retries: " (req ::retries)
                          ", max: " (req :max-retries) ")" ))
          (p/bind timeout
            (fn [_]
              (let [req (-> req
                            (merge (select-keys error [:time-offset]))
                            (update ::retries inc))]
                (issue-retrying! req)))))))))

(defn issue!
  "Return a promise resolving either to a map having keys `:request`,
  `:response` and `:body`, or rejected with an `ExceptionInfo`
  instance (associated with a map having keys `:request`, `:response` and
  `:error`).

  The promise will only be rejected if the remote service returns an
  unrecoverable error, is unreachable, or the maximum number of
  retries (service-specific, may be overridden per-request) is exhausted."
  [req]
  (-> req prepare-req (assoc ::retries 0) issue-retrying!))
