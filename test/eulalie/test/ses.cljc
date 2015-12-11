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
        {{:keys [message] :as body} :body} (eulalie/prepare-request input)]
    (is (= "dan@samsungaccelerator.com" (body [:destination :to-addresses :member 1])))
    (is (= {:text {:encoding "UTF-8" :data "test body"}} (message :body)))
    (is (= {:encoding "UTF-9" :data "test subject"} (message :subject)))
    (is (= "info@unclipapp.com" (body :source)))))


;; params {:Source "info@unclipapp.com"
;;         :Destination {:ToAddresses [email]}
;;         :Message {:Subject {:Data subject}
;;                   :Body {:Html {:Data email-body}}}}
