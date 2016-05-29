(ns eulalie.core
  (:require [eulalie.sign :as sign]
            [eulalie.util.service :as util.service]
            [eulalie.util :as util]
            [eulalie.platform :as platform]
            [taoensso.timbre :as log]
            [kvlt.util :refer [pprint-str]]
            [eulalie.creds :as creds]
            [eulalie.http :as http]
            [#? (:clj clojure.core.async :cljs cljs.core.async) :as async]
            [glossop.core :as g
             #? (:clj :refer :cljs :refer-macros) [go-catching <?]]))

(defn quiet! []
  (log/merge-config! {:ns-blacklist ["eulalie.*" "kvlt.*"]}))

#? (:cljs
    (try
      (.install (js/require "source-map-support"))
      (catch :default _)))

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

(defn prepare-req [{:keys [endpoint headers] :as req}]
  (let [req (-> req
                prepare-request
                (update-in [:endpoint] util.service/concretize-port))
        body (transform-request-body req)]
    (-> req
        (assoc :body body)
        (update-in [:headers] merge
                   {:content-length (platform/byte-count body)}))))

(defn ok? [{:keys [status]}]
  (and status (<= 200 status 299)))

(defn parse-error [{:keys [headers body] :as resp}]
  (if (resp :transport)
    resp
    (util.service/decorate-error
     (let [e (util.service/headers->error-type headers)]
       (or (transform-response-error (assoc resp :error {:type e}))
           {:type (or e :unrecognized)}))
     resp)))

(defn handle-result
  [{aws-resp :response
    retries  :retries
    {:keys [max-retries] :as req} :request :as result}]

  (if-not (platform/response-checksum-ok? aws-resp)
    [:error {:type :crc32-mismatch}]
    (if (ok? aws-resp)
      [:ok (transform-response-body aws-resp)]
      (let [error (parse-error aws-resp)]
        (if (and (util.service/retry? (aws-resp :status) error)
                 (< retries max-retries))
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
          (let [request (cond-> request
                          (:eulalie/type creds)
                          (assoc :creds (<? (creds/creds->credentials creds))))
                request' (sign-request request)]
            (log/debug "Issuing\n" (pprint-str request'))
            (let [aws-resp (-> request' http/request! <?)
                  result   {:response (-> aws-resp
                                          (dissoc :opts)
                                          (assoc :request request))
                            :retries  retries
                            :request  request'}]
              (log/info "Received\n" (pprint-str (result :response)))
              (let [[label value] (handle-result result)]
                (case label
                  :ok    (assoc result :body  value)
                  :error (assoc result :error value)
                  :retry
                  (let [{:keys [timeout error]} value
                        request (merge request (select-keys error [:time-offset]))]
                    (some-> timeout <?)
                    (recur request (inc retries)))))))))
    chan (async/pipe chan close?)))

#?(:clj
   (def issue-request!! (comp g/<?! issue-request!)))
