(ns eulalie.test.core
  (:require
   [cemerick.url :refer [url]]
   [eulalie.core :as eulalie]
   [eulalie.platform.time :as platform.time]
   [eulalie.test.platform.time :refer [with-canned-time]]
   [clojure.walk :as walk]
   [eulalie.test.http :refer [with-local-server]]
   #?@ (:clj
        [[clj-time.core :as time]
         [clojure.core.async :as async]
         [clojure.test :refer [is]]
         [eulalie.test.async :refer [deftest]]
         [glossop.core :refer [go-catching <?]]]
        :cljs
        [[cljs-time.core :as time]
         [cemerick.cljs.test]
         [cljs.core.async :as async]]))
  #? (:cljs
      (:require-macros [glossop.macros :refer [<? go-catching]]
                       [eulalie.test.async.macros :refer [deftest]]
                       [cemerick.cljs.test :refer [is]])))

(defmethod eulalie/prepare-request :eulalie.service/test-service [req]
  (merge {:method :post :max-retries 3 :service-name "testservice"} req))
(defmethod eulalie/transform-request-body
  :eulalie.service/test-service [req] (:body req))
(defmethod eulalie/transform-response-body
  :eulalie.service/test-service [resp] (:body resp))
(defmethod eulalie/transform-response-error
  :eulalie.service/test-service [resp] nil)
(defmethod eulalie/sign-request
  :eulalie.service/test-service [req] req)

(deftest ^:integration hostname-no-retry
  (go-catching
    (let [{:keys [retries error]}
          (<? (eulalie/issue-request!
               {:endpoint (url "http://eulalie.invalid")
                :body ""
                :max-retries 0
                :service :test-service}))]
      (is (zero? retries))
      (is (= :unknown-host (:type error))))))

(deftest ^:integration hostname-retry
  (go-catching
    (let [{:keys [retries error]}
          (<? (eulalie/issue-request!
               {:endpoint (url "http://eulalie.invalid")
                :body ""
                :max-retries 1
                :service :test-service}))]
      (is (= 1 retries))
      (is (= :unknown-host (:type error))))))

(deftest ^:integration vague-error
  (with-local-server [{:status 400}]
    (fn [{:keys [url reqs]}]
      (go-catching (loop [] (<? reqs) (recur)))
      (go-catching
        (is (= :unrecognized
               (->
                (eulalie/issue-request!
                 {:endpoint url :body "" :service :test-service})
                <?
                :error
                :type)))))))

(deftest ^:integration retry-ok
  (let [ok-resp {:status 200
                 :headers {"content-type" "text/plain"}
                 :body "hi from retry-ok!"}]
    (with-local-server [{:status 500} ok-resp]
      (fn [{:keys [url]}]
        (go-catching
          (let [{:keys [body retries]}
                (<? (eulalie/issue-request!
                     {:endpoint url :body "" :service :test-service}))]
            (is (= "hi from retry-ok!" body))
            (is (= 1 retries))))))))

(defn response [status & [{:keys [body headers] :or {body ""}}]]
  {:status status
   :headers (walk/stringify-keys
             (merge {:content-type "text/plain"} headers))
   :body body})

(defn skewed-response [time]
  (response
   400
   {:headers {:date (platform.time/time->rfc822 time)
              :x-amzn-errortype "RequestTimeTooSkewed:"}}))

(deftest ^:integration skew-no-retry
  (go-catching
    (let [client-time (time/date-time 2020 01 26 11 21 59)
          server-time (->> 5 time/minutes (time/minus client-time))
          {reqs :reqs {{:keys [type time-offset]} :error} :result}
          (<? (with-local-server [(skewed-response server-time)]
                (fn [{:keys [url]}]
                  (with-canned-time client-time
                    eulalie/issue-request!
                    {:endpoint url :body "" :max-retries 0 :service :test-service}))))]
      (is (= :request-time-too-skewed type))
      (is (= (* 1000 60 5) time-offset)))))

(defn lose-msecs [t]
  (time/minus
   t
   (time/millis (time/milli t))))

(deftest ^:integration skew-retry
  (go-catching
    (let [client-time (lose-msecs (time/now))
          server-time (time/minus client-time (time/years 1) (time/seconds 1))
          token       (str "skew-retry-" client-time)
          {reqs :reqs {:keys [body time-offset]} :result}
          (<? (with-local-server [(skewed-response server-time)
                                  (response 200 {:body token})]
                (fn [{:keys [url]}]
                  (with-canned-time client-time
                    eulalie/issue-request!
                    {:endpoint url :body "" :max-retries 1 :service :test-service}))))]
      (is (= body token))
      (is (nil? time-offset)))))
