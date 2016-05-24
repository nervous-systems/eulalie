(ns eulalie.test.core
  (:require
   [cemerick.url :refer [url]]
   [eulalie.core :as eulalie]
   [eulalie.platform]
   [eulalie.platform.time :as platform.time]
   [clojure.walk :as walk]
   [eulalie.test.common #? (:clj :refer :cljs :refer-macros) [deftest is]]
   [glossop.core #? (:clj :refer :cljs :refer-macros) [go-catching <?]]
   #? (:clj
       [clj-time.core :as time]
       :cljs
       [cljs-time.core :as time])))

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

(deftest parse-error-unrecognized
  (is (= (eulalie/parse-error {:request {:service :eulalie.service/test-service}})
         {:type :unrecognized})))


(deftest initial-retry
  (let [req     {:service :eulalie.service/test-service :max-retries 1}
        [tag m] (eulalie/handle-result
                 {:response {:request req :status 500}
                  :request  req
                  :retries  0})]
    (is (= tag :retry))
    (is (nil? (get m :timeout ::not-found)))))

(defn response [& [{:keys [status body headers] :or {body "" status 200}}]]
  {:status  status
   :headers (merge {:content-type "text/plain"} headers)
   :body    body})

(defn skewed-response [t]
  (response
   {:status 400
    :headers {:date             (platform.time/time->rfc822 t)
              :x-amzn-errortype "RequestTimeTooSkewed:"}}))

(defn handle-skewed-result [t retries]
  (let [req     {:service :eulalie.service/test-service :max-retries retries}
        resp    (skewed-response t)]
    (eulalie/handle-result
     {:retries 0
      :request req
      :response (assoc resp :request req)})))

(deftest skew-no-retry
  (let [[tag m] (handle-skewed-result (time/date-time 1951) 0)]
    (is (= :error tag))
    (is (= :request-time-too-skewed (m :type)))
    (is (number? (m :time-offset)))))

(deftest skew-retry
  (let [[tag {:keys [error] :as m}] (handle-skewed-result (time/date-time 1900) 1)]
    (is (= :retry tag))
    (is (nil? (get m :timeout ::not-found)))
    (is (= :request-time-too-skewed (error :type)))
    (is (number? (error :time-offset)))))
