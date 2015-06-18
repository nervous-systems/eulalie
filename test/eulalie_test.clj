(ns eulalie-test
  (:require
   [cemerick.url :refer [url]]
   [eulalie.service-util :refer [aws-date-time-format time->rfc822]]
   [eulalie :refer :all]
   [eulalie.util :refer :all]
   [eulalie.sign :as sign]
   [clojure.core.async :as async]
   [eulalie.test-util :refer :all]
   [clojure.test :refer :all]
   [org.httpkit.server :as http]
   [clj-time.format :as time-format]
   [clj-time.coerce :as time-coerce]
   [clj-time.core :as time]
   [clojure.walk :as walk])
  (:import org.joda.time.DateTimeUtils))

(defn start-local-server! [f]
  (let [stop-server (http/run-server f {:port 0})
        {:keys [local-port]} (meta stop-server)]
    {:port local-port :stop! stop-server}))

(defmethod prepare-request :eulalie.service/test-service [req]
  (merge {:method :post :max-retries 3 :service-name "testservice"} req))
(defmethod transform-request-body   :eulalie.service/test-service [req] req)
(defmethod transform-response-body  :eulalie.service/test-service [req body] body)
(defmethod transform-response-error :eulalie.service/test-service [req resp] nil)

(def issue-request* (fn-> (assoc :service :test-service) issue-request!!))

(defn with-local-server [resps bodyf]
  (let [resps (cond->> resps
                (coll? resps) async/to-chan)
        reqs  (atom [])
        {:keys [port stop!]}
        (start-local-server!
         (fn [req]
           (swap! reqs conj (walk/keywordize-keys req))
           (async/<!! resps)))
        result (atom nil)]
    (try
      (let [result (bodyf {:port port
                           :url  (url (str "http://localhost:" port))
                           :reqs reqs})]
        {:reqs @reqs :result result})
      (finally
        (stop!)))))

(deftest ^:integration hostname-no-retry
  (let [{:keys [retries error]}
        (issue-request*
         {:endpoint (url "http://eulalie.invalid")
          :body ""
          :max-retries 0})]
    (is (zero? retries))
    (is (= :unknown-host (:type error)))))

(deftest ^:integration hostname-retry
  (let [{:keys [retries error]}
        (issue-request*
         {:endpoint (url "http://eulalie.invalid")
          :body ""
          :max-retries 1})]
    (is (= 1 retries))
    (is (= :unknown-host (:type error)))))

(deftest ^:integration vague-error
  (with-local-server [{:status 400}]
    (fn [{:keys [url]}]
      (is (= :unrecognized
             (->
              (issue-request* {:endpoint url :body ""})
              :error
              :type))))))

(deftest ^:integration retry-ok
  (let [ok-resp {:status 200
                 :headers {"content-type" "text/plain"}
                 :body "hi from retry-ok!"}]
    (with-local-server [{:status 500} ok-resp]
      (fn [{:keys [url]}]
        (let [{:keys [body retries]}
              (issue-request* {:endpoint url :body ""})]
          (is (= "hi from retry-ok!" body))
          (is (= 1 retries)))))))

(defn with-canned-time [t f & args]
  (DateTimeUtils/setCurrentMillisFixed (time-coerce/to-long t))
  (try
    (apply f args)
    (finally
      (DateTimeUtils/setCurrentMillisOffset 0))))

(defn response [status & [{:keys [body headers] :or {body (rand-string)}}]]
  {:status status
   :headers (walk/stringify-keys
             (merge {:content-type "text/plain"} headers))
   :body body})

(defn skewed-response [time]
  (response
   400
   {:headers {:date (time->rfc822 time)
              :x-amzn-errortype "RequestTimeTooSkewed:"}}))

(def request-dates
  (partial map (fn-some->>
                :headers
                :x-amz-date
                (time-format/parse aws-date-time-format))))

(deftest ^:integration skew-no-retry
  (let [client-time (time/date-time 2020 01 26 11 21 59)
        server-time (->> 5 time/minutes (time/minus client-time))
        {reqs :reqs {{:keys [type time-offset]} :error} :result}
        (with-local-server [(skewed-response server-time)]
          (fn [{:keys [url]}]
            (with-canned-time client-time
              issue-request* {:endpoint url :body "" :max-retries 0})))]
    (is (= :request-time-too-skewed type))
    (is (= (* 1000 60 5) time-offset))
    (is (= client-time (first (request-dates reqs))))))

(defn lose-msecs [t]
  (time/minus
   t
   (time/millis (time/milli t))))

(deftest ^:integration skew-retry
  (let [client-time (lose-msecs (time/now))
        server-time (time/minus client-time (time/years 1) (time/seconds 1))
        token       (rand-string)
        {reqs :reqs {:keys [body time-offset]} :result}
        (with-local-server [(skewed-response server-time)
                            (response 200 {:body token})]
          (fn [{:keys [url]}]
            (with-canned-time client-time
              issue-request* {:endpoint url :body "" :max-retries 1})))]
    (is (= body token))
    (is (nil? time-offset))
    (is (= server-time (last (request-dates reqs))))))
