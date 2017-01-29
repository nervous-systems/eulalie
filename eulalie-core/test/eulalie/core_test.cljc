(ns eulalie.core-test
  (:require [eulalie.request]
            [eulalie.core       :as eulalie]
            [cemerick.url       :as url]
            [eulalie.service    :as service]
            [promesa-check.util :as util]
            [promesa.core       :as p]
            #?(:clj  [clojure.test :as t]
               :cljs [cljs.test :as t :include-macros true])
            [#?(:clj clojure.spec.test :cljs cljs.spec.test) :as stest]
            [#?(:clj clojure.spec :cljs cljs.spec) :as s]))

(stest/instrument)

(taoensso.timbre/merge-config! {:level :warn})

(defmethod service/defaults :eulalie.service/test-service [_]
  {:eulalie.request/method       :post
   :eulalie.request/max-retries  3
   :eulalie.sign/service         "testservice"})

(defmethod service/transform-request-body :eulalie.service/test-service [req]
  (req :eulalie.service.test-service/body))

(s/def :eulalie.service.test-service/body any?)

(defmethod eulalie.request/service->spec :eulalie.service/test-service [_]
  (s/keys :req [:eulalie.service.test-service/body]))

(defmethod service/sign-request
  :eulalie.service/test-service [req] req)

(defn- issue! [& [m]]
  (eulalie/issue!
   (merge
    #:eulalie.request{:endpoint (url/url "http://eulalie.invalid")
                      :target   :any
                      :service  :eulalie.service/test-service
                      :creds    {:access-key "" :secret-key ""}
                      :eulalie.service.test-service/body ""}
    m)))

(defn error [{:keys [status type req] :or {status 0 type :transport}}]
  {:eulalie.response/status status
   :eulalie.response/error  {:eulalie.error/type type
                             :eulalie/request    req}
   :eulalie/request         req})

(defn ok [{:keys [req status body] :or {status 200 body ""}}]
  #:eulalie.response{:body            body
                     :status          status
                     :headers         {}
                     :eulalie/request req})

(util/deftest no-retries
  (defmethod service/issue-request! :eulalie.service/test-service [req]
    (p/resolved (error {:req req})))

  (p/alet [[tag e] (p/await (-> (issue! {:eulalie.request/max-retries 0})
                                (p/branch
                                  (fn [m] [:ok m])
                                  (fn [e] [:error e]))))]
    (t/is (zero? (-> e ex-data :eulalie/request :eulalie.request/retries)))
    (when-not (= :transport (-> e ex-data :eulalie.error/type))
      (throw e))))

(util/deftest retry
  (let [reqs (atom 0)]
    (defmethod service/issue-request! :eulalie.service/test-service [req]
      (p/resolved (if (= 1 (swap! reqs inc))
                    (error {:req req :status 500 :type :http})
                    (ok    {:req req}))))
    (p/alet [resp (p/await (issue! {:eulalie.request/max-retries 1}))]
      (t/is (= 2 @reqs))
      (t/is (= 200 (-> resp :eulalie.response/status)))
      (t/is (not (resp :eulalie.error/type))))))

(util/deftest success
  (defmethod service/issue-request! :eulalie.service/test-service [req]
    (p/resolved (ok {:req req :body ::impenetrable})))

  (p/alet [resp (p/await (issue!))]
    (t/is (= ::impenetrable (resp :eulalie.response/body)))
    (t/is (= 200 (resp :eulalie.response/status)))
    (t/is (zero? (-> resp :eulalie/request :eulalie.request/retries)))
    (t/is (not (resp :eulalie.error/type)))))
