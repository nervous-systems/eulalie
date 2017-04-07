(ns eulalie.dynamo.integration-test
  (:require #?(:cljs [clojure.test.check])
            [eulalie.service :as service]
            [eulalie.dynamo]
            [clojure.test.check.properties :as prop]
            [#?(:clj clojure.spec.test :cljs cljs.spec.test) :as stest]
            [camel-snake-kebab.core :as csk]
            [eulalie.core :as eulalie]
            [promesa-check.core :as pc]
            [promesa-check.util :as t]
            [#?(:clj clojure.spec :cljs cljs.spec) :as s]
            [eulalie.service.impl.test.util :refer [un-ns]]
            [promesa.core :as p]))

(taoensso.timbre/merge-config! {:level :warn})
(stest/instrument)

(defn- body->target [body]
  (->> body :eulalie.dynamo/target csk/->PascalCaseString (str "DynamoDB_20120810.")))

(defn- resp->target [resp]
  (-> resp :eulalie/request :eulalie.request/headers :x-amz-target))

(defn- echoing! []
  (defmethod service/issue-request! :eulalie/dynamo [req]
    (p/resolved #:eulalie.response
                {:status          200
                 :body            (req :eulalie.request/body)
                 :eulalie/request req
                 :headers         {}})))

(pc/defspec issue 50
  (do
    (echoing!)
    (prop/for-all* [(s/gen :eulalie.dynamo.request/body)]
      (fn [body]
        (let [req {:eulalie.request/service     :eulalie/dynamo
                   :eulalie.request/creds       {:access-key "" :secret-key ""}
                   :eulalie.dynamo.request/body body}]
          (-> (eulalie/issue! req)
              (p/then
                (fn [resp]
                  (and (= (resp :eulalie.response/body)
                          (un-ns (clojure.walk/keywordize-keys body)))
                       (= (resp->target resp) (body->target body)))))))))))
