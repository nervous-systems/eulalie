(ns eulalie
  (:require
   [eulalie.sign :as sign]
   [org.httpkit.client :as http]
   [clojure.tools.logging :as log]
   [clojure.walk :as walk]
   [clojure.core.async :as async]
   [cemerick.url :refer [url]]
   [eulalie.service-util :refer :all]
   [eulalie.util :refer :all]))

(defn channel-request! [m]
  (let [ch (async/chan)]
    (http/request m #(close-with! ch %))
    ch))

(def req->http-kit
  (map-rewriter
   [:endpoint :url
    :url      str
    :headers  walk/stringify-keys]))

(defn req->service-value [{:keys [service]}]
  (keyword "eulalie.service" (name service)))

(defmulti prepare-request   req->service-value)
(defmulti transform-request-body req->service-value)

(defmulti transform-response-body  (fn [req body] (req->service-value req)))
(defmulti transform-response-error (fn [req resp] (req->service-value req)))

(defmulti  request-backoff (fn [req retries error] (req->service-value req)))
(defmethod request-backoff :default [_ retries error]
  (default-retry-backoff retries error))

(defmulti  sign-request req->service-value)
(defmethod sign-request :default [{:keys [service-name] :as req}]
  ;; After prepare-request, we expect there to be a canonical service name on
  ;; the request, e.g. "dynamodb", rather than :dynamo
  (sign/aws4-sign service-name req))

(defn prepare-req
  [{:keys [endpoint headers] :as req}]

  (let [{:keys [body] :as req}
        (-> req
            prepare-request
            transform-request-body
            (update-in [:endpoint] concretize-port))]
    ;; this needs to go away, can't assume it can be counted now
    (update-in req [:headers] merge
               {:content-length (count (get-utf8-bytes body))})))

(def ok? (fn-> :status (= 200)))

(defn parse-error [req {:keys [headers body] :as resp}]
  (decorate-error
   (if-let [e (headers->error-type headers)]
     {:type e}
     (or (transform-response-error req resp)
         {:type :unrecognized}))
   resp))

(defn handle-result
  [{aws-resp :response
    retries  :retries
    {:keys [max-retries] :as req} :request :as result}]

  (if-not (response-checksum-ok? aws-resp)
    [:error {:type :crc32-mismatch}]
    (if (ok? aws-resp)
      [:ok (->> aws-resp :body (transform-response-body req))]
      (let [error (or (http-kit->error (:error aws-resp))
                      (parse-error req aws-resp))]
        (if (and (retry? (:status aws-resp) error) (< retries max-retries))
          [:retry {:timeout (request-backoff req retries error)
                   :error   error}]
          [:error error])))))

(defn issue-request!
  [{:keys [service creds region] :as request}]
  (go-catching
    (loop [request (prepare-req request)
           retries 0]
      (let [request' (sign-request request)
            aws-resp (-> request' req->http-kit channel-request! <?)
            result   {:response (dissoc aws-resp :opts)
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
  (<?! (apply issue-request! args)))

(def make-client-state (partial merge {:jvm-time-offset 0}))

(let [client-state (atom (make-client-state))]
  (defn issue-request!* [{:keys [time-offset] :as request}]
    (go-catching
      (let [request (cond-> request
                      (not time-offset)
                      (assoc :time-offset
                             (-> client-state deref :jvm-time-offset)))
            response (<? (issue-request! request))]
        (swap! client-state assoc
               :jvm-time-offset (-> response :request :time-offset))
        response)))

  (defn issue-request!!* [& args]
    (<?! (apply issue-request!* args))))
