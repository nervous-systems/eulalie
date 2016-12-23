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

(defmethod service/request-defaults :eulalie.service/test-service [_]
  (println "OK")
  {:method               :post
   :max-retries          3
   :eulalie.sign/service "testservice"})
(defmethod service/sign-request
  :eulalie.service/test-service [req] req)
(defmethod service/issue-request!
  :eulalie.service/test-service [req]
  (@request* req))

(defn- issue! [& [m]]
  (eulalie/issue!
   (merge
    {:endpoint (url/url "http://eulalie.invalid")
     :body     ""
     :creds    {}
     :service  :test-service} m)))

(util/deftest no-retries
  (defmethod service/issue-request! :eulalie.service/test-service [req]
    (p/resolved {:status 0 :eulalie.core/transport-error? true}))

  (p/alet [resp (p/await (issue! {:max-retries 0}))]
    (t/is (= :transport (-> resp :error :type)))
    (t/is (zero? (-> resp :request :eulalie.core/retries)))))

(util/deftest retry
  (let [reqs (atom 0)]
    (defmethod service/issue-request! :eulalie.service/test-service [req]
      (let [reqs (swap! reqs inc)]
        (p/resolved {:status (if (= 1 reqs) 500 200)})))

    (p/alet [resp (p/await (issue! {:max-retries 1}))]
      (t/is (= 2 @reqs))
      (t/is (= 200 (-> resp :response :status)))
      (t/is (not (resp :error))))))

(util/deftest success
  (defmethod service/issue-request! :eulalie.service/test-service [req]
    (p/resolved {:status 200 :body ::impenetrable}))

  (p/alet [resp (p/await (issue!))]
    (t/is (= ::impenetrable (resp :body)))
    (t/is (= 200 (-> resp :response :status)))
    (t/is (not (resp :error)))))
