(ns eulalie
  (:require
   [org.httpkit.client :as http]
   [clojure.tools.logging :as log]
   [clojure.walk :as walk]
   [clojure.core.async :as async]
   [clojure.set :refer [rename-keys]]
   [cemerick.url :refer [url]]
   [eulalie.service-util :refer :all]
   [eulalie.util :refer :all]))

(defn channel-request! [m]
  (clojure.pprint/pprint m)
  (let [ch (async/chan)]
    (http/request m #(close-with! ch %))
    ch))

(def req->http-kit
  (map-rewriter
   [:endpoint :url
    :url      str
    :headers  walk/stringify-keys
    :content  :body]))

(defprotocol AmazonWebService
  (prepare-request    [this req])
  (transform-request  [this req])
  (transform-response [this resp])
  (transform-response-error [this resp])
  (request-backoff    [this retry-count error])
  (sign-request       [this creds req]))

(defn prepare-req
  [{:keys [endpoint headers] :as req} service]

  (let [{:keys [content] :as req}
        (-> (prepare-request service req)
            (update-in [:content] #(transform-request service %))
            (update-in [:endpoint] concretize-port))]
    ;; this needs to go away, can't assume it can be counted now
    (update-in req [:headers] merge {:content-length (count content)})))

(def ok? (fn-> :status (= 200)))

(defn parse-error [service {:keys [headers body] :as resp}]
  (decorate-error
   {:type    (or
              (headers->error-type headers)
              (transform-response-error service resp)
              :unrecognized)
    ;; This is transparently incorrect, ask service for the whole ;; thing
    :message (:message body)}
   resp))

(defn handle-result
  [service
   {aws-resp :response
    retries  :retries
    {:keys [max-retries]} :request :as result}]

  (if-not (response-checksum-ok? aws-resp)
    [:error {:type :crc32-mismatch}]
    (if (ok? aws-resp)
      [:ok (->> aws-resp :body (transform-response service))]
      (let [error (or (http-kit->error (:error aws-resp))
                      (parse-error service aws-resp))]
        (if (and (retry? (:status aws-resp) error) (< retries max-retries))
          [:retry {:timeout (request-backoff service retries error)
                   :error   error}]
          [:error error])))))

(defn issue-request!
  [service creds request]
  (go-catching
   (loop [request (prepare-req request service)
          retries 0]
     (let [request' (sign-request service creds request)
           aws-resp (-> request' req->http-kit channel-request! <?)
           result   {:response (dissoc aws-resp :opts)
                     :retries  retries
                     :request  request'}
           [label value] (handle-result service result)]
       (condp = label
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
  (defn issue-request!* [service creds {:keys [time-offset] :as request}]
    (go-catching
     (let [request (cond-> request
                     (not time-offset)
                     (assoc :time-offset
                            (-> client-state deref :jvm-time-offset)))
           response (<? (issue-request! creds service request))]
       (swap! client-state assoc
              :jvm-time-offset (-> response :request :time-offset))
       response)))

  (defn issue-request!!* [& args]
    (<?! (apply issue-request!* args))))
