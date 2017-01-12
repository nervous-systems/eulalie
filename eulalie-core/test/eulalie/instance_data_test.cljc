(ns eulalie.instance-data-test
  (:require [eulalie.instance-data :as data]
            [cemerick.url :refer [url]]
            [kvlt.core :as kvlt]
            #?(:clj  [clojure.test :as t]
               :cljs [cljs.test :as t :include-macros true])
            [promesa.core :as p]
            [promesa.impl.proto :refer [IPromise]]
            [promesa-check.util :as util]))

(def creds-path       "/latest/meta-data/iam/security-credentials")
(def expiration-stamp "2012-04-27T22:39:16Z")
(def expiration       1335566356000)

(t/deftest retrieve
  (with-redefs [kvlt/request! (fn [req]
                                (reify IPromise
                                  (-map [_ cb]
                                    (cb {:body req}))))]
    (t/testing "Text"
      (let [req (data/retrieve! [:latest :dynamic :xyz])]
        (t/is (= (-> req :url url :path) "/latest/dynamic/xyz"))
        (t/is (not= (req :as) :json))))
    (t/testing "JSON"
      (t/is (= :json (-> (data/retrieve! [:xyz] {:parse-json? true}) :as))))))

(util/deftest default-iam-role
  (with-redefs
    [data/retrieve!
     (fn [path & _]
       (t/is (= path [:latest :meta-data :iam :security-credentials]))
       (p/resolved "the-role\nok"))]
    (p/then (data/default-iam-role!)
      (fn [role]
        (t/is (= "the-role" ))))))

(util/deftest iam-credentials
  (let [creds {:access-key-id     "access-key"
               :secret-access-key "secret-key"
               :token             "the-token"}]
    (with-redefs
      [data/retrieve!
       (fn [path & _]
         (t/is (= path [:latest :meta-data :iam :security-credentials "some-role"]))
         (p/resolved (assoc creds :expiration expiration-stamp)))]
      (p/then (data/iam-credentials! "some-role")
        (fn [creds]
          (t/is (= creds (assoc creds :expiration expiration))))))))
