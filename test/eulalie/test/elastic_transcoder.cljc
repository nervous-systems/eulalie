(ns eulalie.test.elastic-transcoder
  (:require [glossop.core #? (:clj :refer :cljs :refer-macros) [go-catching <?]]
            [eulalie.test.common #? (:clj :refer :cljs :refer-macros) [deftest is]]
            [eulalie.core :as eulalie]
            [eulalie.elastic-transcoder]
            [eulalie.test.common :as test.common]
            [eulalie.util :refer [env!]]))

(defn transcoder! [creds target content & [req-overrides]]
  (println "called with target" target)
  (go-catching
   (let [req (merge
              {:service :elastic-transcoder
               :target target
               :max-retries 0
               :body content
               :creds creds}
              req-overrides)]
     (<? (test.common/issue-raw! req)))))

(deftest get-pipelines-test!
  (test.common/with-aws
    (fn [creds]
      (go-catching
       (let [response (<? (transcoder!
                           creds
                           :jobs-by-status
                           {:status "Progressing" :ascending true}))]
         (println "response" response)
          (is (string? response)))))))
