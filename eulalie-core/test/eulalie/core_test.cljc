(ns eulalie.core-test
  (:require [eulalie.core      :as eulalie]
            [eulalie.impl.http :as http]
            [cemerick.url      :as url]
            [eulalie.service   :as service]
            [eulalie.test-util :as util]
            [promesa.core      :as p]
            #?(:clj  [clojure.test :as t]
               :cljs [cljs.test :as t :include-macros true])))

(defonce request* (atom identity))

(defmethod service/prepare-request :eulalie.service/test-service [req]
  (merge {:method :post :max-retries 3 :service-name "testservice"} req))
(defmethod service/transform-request-body
  :eulalie.service/test-service [req] (:body req))
(defmethod service/transform-response-body
  :eulalie.service/test-service [resp] (:body resp))
(defmethod service/transform-response-error
  :eulalie.service/test-service [resp] nil)
(defmethod service/sign-request
  :eulalie.service/test-service [req] req)
(defmethod service/issue-request!
  :eulalie.service/test-service [req]
  (@request* req))

(util/deftest no-retries
  (reset! request* (fn [_]
                     (p/resolved
                      {:status 0 :eulalie.core/transport-error? true})))
  (p/alet [resp (p/await
                 (eulalie/issue!
                  {:endpoint    (url/url "http://eulalie.invalid")
                   :body        ""
                   :creds       {}
                   :max-retries 0
                   :service     :test-service}))]
    (t/is (= :transport (-> resp :error :type)))
    (t/is (zero? (-> resp :request :eulalie.core/retries)))))

(util/deftest retry
  (let [reqs (atom 0)]
    (reset! request* (fn [req]
                     (let [reqs (swap! reqs inc)]
                       (p/resolved {:status (if (= 1 reqs) 500 200)}))))

    (p/alet [resp (p/await
                   (eulalie/issue!
                    {:endpoint    (url/url "http://eulalie.invalid")
                     :body        ""
                     :creds       {}
                     :max-retries 1
                     :service     :test-service}))]
      (t/is (= 2 @reqs))
      (t/is (= 200 (-> resp :response :status)))
      (t/is (not (resp :error))))))

