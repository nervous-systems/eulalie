(ns eulalie.core
  (:require [eulalie.service       :as service]
            [eulalie.impl.platform :as platform]
            [eulalie.impl.service  :as service-util]
            [eulalie.creds         :as creds]
            [taoensso.timbre :as log]
            [promesa.core    :as p]
            [kvlt.core       :as kvlt]
            [kvlt.util :refer [pprint-str]]))

(defn prepare-req [{:keys [endpoint headers] :as req}]
  (let [req  (-> req
                 service/prepare-request
                 (update :endpoint service-util/concretize-port))
        body (service/transform-request-body req)]
    (-> req
        (assoc :body body)
        (assoc-in [:headers :content-length] (platform/byte-count body)))))

(defn- ok? [{:keys [status]}]
  (and status (<= 200 status 299)))

(defn- parse-error [{:keys [headers body] :as resp}]
  (if (resp :transport)
    resp
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

(defn- request! [req]
  (kvlt/request! (-> req
                     (dissoc :endpoint)
                     (assoc :url (str (req :endpoint))))))

(defn issue-retrying! [{:keys [creds] :as req}]
  (p/alet [creds (p/await (cond-> creds (creds/expired? creds) creds/refresh!))
           req   (assoc req :creds creds)
           req'  (service/sign-request req)]
    (log/debug "Issuing\n" (pprint-str req'))
    (p/alet [resp   (p/await (request! req'))
             result {:response (-> resp (dissoc :opts) (assoc :request req))
                     :request  req'}
             [label value] (handle-result result)]
      (case label
        :ok    (assoc result :body  value)
        :error (assoc result :error value)
        :retry
        (p/then (value :timeout)
          (fn [_]
            (let [req (-> req
                          (merge (select-keys (value :error) [:time-offset]))
                          (update ::retries inc))]
              (issue-retrying! req))))))))

(defn issue!
  [{:keys [service creds region] :as req}]
  (let [req (prepare-req req)]
    (issue-retrying! (assoc req ::retries 0))))
