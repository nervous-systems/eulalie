(ns eulalie.test.ses
  (:require [eulalie.core :as eulalie]
            [eulalie.ses]
            [eulalie.util.xml :as x]
            #? (:clj [clojure.test :refer [deftest is]]
                :cljs [cljs.test :refer-macros [deftest is]])))

(defn make-request [target content & [req-overrides]]
  (merge
   {:service :ses
    :target  target
    :body content}
   req-overrides))

(deftest format-email-request
  (let [input (make-request :send-email {:to ["dan@samsungaccelerator.com"]
                                         :text "test body"
                                         :subject {:encoding "UTF-9" :data "test subject"}
                                         :source "info@unclipapp.com"})
        {:keys [body]} (eulalie/prepare-request input)]
    (is (= "dan@samsungaccelerator.com" (body [:destination :to-addresses :member 1])))
    (is (= "UTF-8" (body [:message :body :text :encoding])))
    (is (= "test body" (body [:message :body :text :data])))
    (is (= "UTF-9" (body [:message :subject :encoding])))
    (is (= "test subject" (body [:message :subject :data])))
    (is (= "info@unclipapp.com" (body :source)))))

(deftest verify-email-response
  (let [req (make-request :send-email {})
        message-id "000001519c8bf8ff-8f4cea40-1242-482c-90cb-63335cd4a772-000000"
        body (str "<SendEmailResponse xmlns=\"http://ses.amazonaws.com/doc/2010-12-01/\">"
                  "<SendEmailResult>"
                  "<MessageId>"
                  message-id
                  "</MessageId>"
                  "</SendEmailResult>"
                  "<ResponseMetadata>"
                  "<RequestId>e1d83475-a1c5-11e5-bace-3fbec8379102</RequestId>"
                  "</ResponseMetadata>"
                  "</SendEmailResponse>")]
    (is (= message-id (eulalie/transform-response-body {:request req :body body})))))
