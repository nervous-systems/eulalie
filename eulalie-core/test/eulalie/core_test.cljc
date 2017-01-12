(ns eulalie.core-test
  (:require [eulalie.core       :as eulalie]
            [cemerick.url       :as url]
            [eulalie.service    :as service]
            [promesa-check.util :as util]
            [promesa.core       :as p]
            #?(:clj  [clojure.test :as t]
               :cljs [cljs.test :as t :include-macros true])))

(taoensso.timbre/merge-config! {:level :warn})

(defmethod service/defaults :eulalie.service/test-service [_]
  {:method               :post
   :max-retries          3
   :eulalie.sign/service "testservice"})

(defmethod service/sign-request
  :eulalie.service/test-service [req] req)

(defn- issue! [& [m]]
  (eulalie/issue!
   (merge
    {:endpoint (url/url "http://eulalie.invalid")
     :body     ""
     :creds    {}
     :service  :test-service} m)))

(util/deftest no-retries
  (defmethod service/issue-request! :eulalie.service/test-service [req]
    (p/resolved {:status 0 :eulalie.error/transport? true}))

  (p/alet [[tag e] (p/await (-> (issue! {:max-retries 0})
                                (p/branch
                                  (fn [m] [:ok m])
                                  (fn [e] [:error e]))))]
    (t/is (= tag :error))
    (t/is (= :transport (-> e ex-data :eulalie.error/type)))
    (t/is (zero? (-> e ex-data :request :eulalie.core/retries)))))

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
