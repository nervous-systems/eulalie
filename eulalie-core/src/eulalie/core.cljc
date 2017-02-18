(ns eulalie.core
  "Responsible for transforming maps describing remote service operations, and
  issuing them as HTTP requests to AWS.  The majority of this work is done by
  delegating to service-specific functionality (see [[eulalie.service]]).

  Requests are described by the `:eulalie/request` spec, and responses by
  `:eulalie/response`.  The request's `:eulalie.request/service` keyword used
  for method dispatch in [[eulalie.service]].  The namespace containing the
  appropriate method definitions must be loaded, even if not used directly."
  (:require [eulalie.service       :as service]
            [eulalie.impl.platform :as platform]
            [eulalie.impl.service  :as service-util]
            [eulalie.creds         :as creds]
            [taoensso.timbre       :as log]
            [promesa.core          :as p]
            [kvlt.util :refer [pprint-str]]
            [eulalie.request]
            [eulalie.response]
            [#?(:clj clojure.spec :cljs cljs.spec) :as s]))

(defn- prepare-req [req]
  (let [defaults (service/defaults (req :eulalie.request/service))
        req      (-> req
                     (service-util/default-request defaults)
                     service/prepare-request
                     (update :eulalie.request/endpoint service-util/explicit-port))
        body     (service/transform-request-body req)]
    (-> req
        (assoc :eulalie.request/body body)
        (assoc-in [:eulalie.request/headers :content-length]
                  (platform/byte-count body)))))

(s/fdef prepare-req
  :args (s/cat :req :eulalie/request)
  :ret  :eulalie.request/prepared)

(defn- ok? [{:keys [eulalie.response/status] :as resp}]
  (and status (<= 200 status 299) (not (resp :eulalie.response/error))))

(defn- parse-error [{:keys [eulalie.response/headers
                            eulalie.response/body
                            eulalie.response/error] :as resp}]
  (or error
      (service-util/decorate-error
       (let [e (service-util/headers->error-type headers)]
         (or (service/transform-response-error
              (assoc resp :eulalie.response/error {:eulalie.error/type e}))
             {:eulalie.error/type (or e :unrecognized)}))
       resp)))

(s/fdef parse-error
  :args (s/cat :resp :eulalie/response)
  :ret  :eulalie/error)

(defn- handle-response [resp]
  (if-not (service-util/response-checksum-ok? resp)
    [:error {:eulalie.error/type :crc32-mismatch}]
    (if (ok? resp)
      [:ok (assoc resp
             :eulalie.response/body (service/transform-response-body resp))]
      (let [error (parse-error resp)
            req   (resp :eulalie/request)]
        (if (and (service-util/retry? (resp :eulalie.response/status) error)
                 (< (req :eulalie.request/retries) (req :eulalie.request/max-retries)))
          [:retry {::timeout      (service/request-backoff req error)
                   :eulalie/error error}]
          [:error error])))))

(s/def ::timeout int?)

(s/fdef handle-response
  :args (s/cat :resp :eulalie/response)
  :ret  (s/or :ok    (s/cat :tag #{:ok}    :value :eulalie/response)
              :retry (s/cat :tag #{:retry} :value (s/keys :req [::timeout :eulalie/error]))
              :error (s/cat :tag #{:error} :value :eulalie/error)))

(defn- assert-explicit-creds [creds]
  #?(:cljs
     (when (and (not creds) (not= cljs.core/*target* "nodejs"))
       (throw (ex-info ":eulalie.request/creds required in non-Node cljs")))))

(defn- with-creds [req]
  (let [creds (req :eulalie.request/creds)]
    (assert-explicit-creds creds)
    (if (creds/expired? creds)
      (-> (creds/refresh! creds)
          (p/then #(assoc req :eulalie.sign/creds %1)))
      (p/resolved (assoc req :eulalie.sign/creds (creds/resolve creds))))))

(defn- issue-retrying! [{:keys [eulalie.request/creds] :as req}]
  (p/alet [req     (p/await (with-creds req))
           signed  (service/sign-request req)]
    (log/debug "Issuing\n" (pprint-str signed))
    (p/alet [resp          (p/await (service/issue-request! signed))
             [label value] (handle-response resp)]
      (case label
        :ok value
        :error
        (let [{:keys [eulalie.error/type eulalie.error/message]} value]
          (throw (ex-info (or message (name type)) value)))
        :retry
        (let [{:keys [::timeout :eulalie/error]} value]
          (log/debug "Retrytable error" error
                     (str "(retries: " (req :eulalie.request/retries)
                          ", max: " (req :eulalie.request/max-retries) ")" ))
          (p/bind timeout
            (fn [_]
              (let [req (-> req
                            (merge (select-keys error [:eulalie.error/time-offset]))
                            (update :eulalie.request/retries inc))]
                (issue-retrying! req)))))))))

(s/fdef issue-retrying!
  :args (s/cat :req :eulalie.request/prepared)
  :ret  p/promise?)

(defn issue!
  "Return a promise resolving to a `:eulalie/response`-compliant map `:body`, or
  rejected with an `ExceptionInfo` instance (associated with a
  `:eulalie/error`-compliant map).

  The promise will only be rejected if the remote service returns an
  unrecoverable error, is unreachable, or the maximum number of
  retries (service-specific, may be overridden per-request) is exhausted."
  [req]
  (-> req prepare-req (assoc :eulalie.request/retries 0) issue-retrying!))

(s/fdef issue!
  :args (s/cat :req :eulalie/request)
  :ret  p/promise?)
