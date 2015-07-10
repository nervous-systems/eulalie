(ns eulalie
  (:require
   [eulalie.sign :as sign]
   [clojure.walk :as walk]
   [clojure.core.async :as async]
   [cemerick.url :refer [url]]
   [eulalie.util.service  :as service]
   [eulalie.util.platform :as platform]
   #?(:clj  [eulalie.util :as util :refer [<?]]
      :cljs [eulalie.util :as util :refer-macros [<?]])))

(def req->http-kit
  (util/map-rewriter
   [:endpoint :url
    :url      str
    :headers  walk/stringify-keys]))

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
  (service/default-retry-backoff retries error))

(defmulti  sign-request req->service-dispatch)
(defmethod sign-request :default [{:keys [service-name] :as req}]
  ;; After prepare-request, we expect there to be a canonical service name on
  ;; the request, e.g. "dynamodb", rather than :dynamo
  (sign/aws4-sign service-name req))

(defn prepare-req
  [{:keys [endpoint headers] :as req}]

  (let [req (-> req
                prepare-request
                (update-in [:endpoint] service/concretize-port))
        body (transform-request-body req)]
    ;; this needs to go away, can't assume it can be counted now
    (-> req
        (assoc :body body)
        (update-in [:headers] merge
                   {:content-length (count (util/get-utf8-bytes body))}))))

(defn ok? [{:keys [status]}]
  (and status (<= 200 status 299)))

(defn parse-error [req {:keys [headers body] :as resp}]
  (service/decorate-error
   (if-let [e (service/headers->error-type headers)]
     {:type e}
     (or (transform-response-error resp)
         {:type :unrecognized}))
   resp))

(defn handle-result
  [{aws-resp :response
    retries  :retries
    {:keys [max-retries] :as req} :request :as result}]

  (if-not (platform/response-checksum-ok? aws-resp)
    [:error {:type :crc32-mismatch}]
    (if (ok? aws-resp)
      [:ok (transform-response-body aws-resp)]
      (let [error (or (service/http-kit->error (:error aws-resp))
                      (parse-error req aws-resp))]
        (if (and (service/retry?
                  (:status aws-resp) error) (< retries max-retries))
          [:retry {:timeout (request-backoff req retries error)
                   :error   error}]
          [:error error])))))

(defn issue-request!
  [{:keys [service creds region] :as request}]
  (util/go-catching
    (loop [request (prepare-req request)
           retries 0]
      (let [request' (sign-request request)
            aws-resp (-> request' req->http-kit util/channel-request! <?)
            result   {:response (-> aws-resp
                                    (dissoc :opts)
                                    (assoc :request request))
                      :retries  retries
                      :request  request'}
            [label value] (handle-result result)]
        (case label
          :ok    (assoc result :body  value)
          :error (assoc result :error value)
          :retry (let [{:keys [timeout error]} value
                       request (merge request (select-keys error [:time-offset]))]
                   (some-> timeout <?)
                   (recur request (inc retries))))))))

(defn issue-request!! [& args]
  (util/<?! (apply issue-request! args)))

(def make-client-state (partial merge {:jvm-time-offset 0}))

(let [client-state (atom (make-client-state))]
  (defn issue-request!* [{:keys [time-offset] :as request}]
    (util/go-catching
      (let [request (cond-> request
                      (not time-offset)
                      (assoc :time-offset
                             (-> client-state deref :jvm-time-offset)))
            response (<? (issue-request! request))]
        (swap! client-state assoc
               :jvm-time-offset (-> response :request :time-offset))
        response)))

  (defn issue-request!!* [& args]
    (util/<?! (apply issue-request!* args))))
