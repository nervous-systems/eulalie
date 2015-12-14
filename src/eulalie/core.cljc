(ns eulalie.core
  (:require [eulalie.sign :as sign]
            [cemerick.url :refer [url]]
            [clojure.tools.logging :as log]
            [eulalie.util.service :as util.service]
            [eulalie.util :as util]
            [eulalie.platform :as platform]
            [eulalie.creds :as creds]
            [#? (:clj
                 clojure.core.async
                 :cljs
                 cljs.core.async) :as async]
            [glossop.core :as g
             #? (:clj :refer :cljs :refer-macros) [go-catching <?]]
            #? (:cljs [cljs.nodejs :as nodejs])))

#? (:cljs
    (try
      (.install (nodejs/require "source-map-support"))
      (catch js/Error _)))

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
  (util.service/default-retry-backoff retries error))

(defmulti  sign-request req->service-dispatch)
(defmethod sign-request :default [{:keys [service-name] :as req}]
  ;; After prepare-request, we expect there to be a canonical service name on
  ;; the request, e.g. "dynamodb", rather than :dynamo
  (sign/aws4-sign service-name req))

(defn prepare-req
  [{:keys [endpoint headers] :as req}]

  (let [req (-> req
                prepare-request
                (update-in [:endpoint] util.service/concretize-port))
        body (transform-request-body req)]
    (-> req
        (assoc :body body)
        (update-in [:headers] merge
                   ;; this needs to go away, can't assume it can be counted now
                   {:content-length (platform/byte-count body)}))))

(defn ok? [{:keys [status]}]
  (and status (<= 200 status 299)))

(defn parse-error [req {:keys [headers body] :as resp}]
  (util.service/decorate-error
   (let [e (util.service/headers->error-type headers)]
     (or (transform-response-error (assoc resp :error {:type e}))
         {:type (or e :unrecognized)}))
   resp))

(defn handle-result
  [{aws-resp :response
    retries  :retries
    {:keys [max-retries] :as req} :request :as result}]

  (if-not (platform/response-checksum-ok? aws-resp)
    [:error {:type :crc32-mismatch}]
    (if (ok? aws-resp)
      [:ok (transform-response-body aws-resp)]
      (let [error (or (platform/http-response->error (:error aws-resp))
                      (parse-error req aws-resp))]
        (if (and (util.service/retry?
                  (:status aws-resp) error) (< retries max-retries))
          [:retry {:timeout (request-backoff req retries error)
                   :error   error}]
          [:error error])))))

(defn issue-request!
  [{:keys [service creds region chan close?]
    :as request :or {close? true}}]
  (cond->
      (go-catching
        (loop [request (prepare-req request)
               retries 0]
          (log/info "Issuing request against" (:service request) (:target request))
          (log/debug "Request" request retries)
          (let [request (cond-> request
                          (:eulalie/type creds)
                          (assoc :creds (<? (creds/creds->credentials creds))))
                request' (sign-request request)
                aws-resp (-> request' platform/channel-aws-request! <?)
                result   {:response (-> aws-resp
                                        (dissoc :opts)
                                        (assoc :request request))
                          :retries  retries
                          :request  request'}
                [label value] (handle-result result)]
            (cond
              (= label :ok)    (do
                                 (log/debug "Response" result)
                                 (assoc result :body  value))
              (= label :error) (do
                                 (log/error value (:service request) (:target request))
                                 (assoc result :error value))
              (= label :retry)
              (let [{:keys [timeout error]} value
                    request (merge request (select-keys error [:time-offset]))]
                (log/warn error "Retrying" (:service request) (:target request) retries)
                (some-> timeout <?)
                (recur request (inc retries)))))))
    chan (async/pipe chan close?)))

#?(:clj
   (def issue-request!! (comp g/<?! issue-request!)))
