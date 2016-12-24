(ns eulalie.service.dynamo.integration-test
  (:require [eulalie.service :as service]
            [eulalie.service.dynamo :as dynamo]
            [clojure.test.check.properties :as prop]
            [eulalie.service.dynamo.test.common :refer [targets]]
            [camel-snake-kebab.core :as csk]
            [eulalie.service.test.util :refer [gen-request]]
            [eulalie.core :as eulalie]
            [promesa-check.util :as t]
            [promesa-check.core :as pc]
            [promesa.core :as p]))

(pc/defspec issue 10
  (prop/for-all* [(gen-request :dynamo targets)]
    (fn [[target body]]
      (defmethod service/issue-request! :eulalie.service/dynamo [req]
        (p/resolved {:status 200 :body (req :body)}))

      (p/then (eulalie/issue! {:service :dynamo
                               :target  target
                               :creds   {:access-key "" :secret-key ""}
                               :body    body})
        (fn [{:keys [request] :as resp}]
          (t/is (= (-> request :headers :content-type) "application/x-amz-json-1.0"))
          (t/is (= (-> request :headers :x-amz-target)
                   (str "DynamoDB_20120810." (csk/->PascalCaseString target))))
          (t/is (= (resp :body) (clojure.walk/keywordize-keys body))))))))
