(ns eulalie.ses
  (:require [eulalie.core :as eulalie]
            [cemerick.url :as url]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as csk-extras]
            [clojure.set :as set]
            [eulalie.util.service :as util.service]
            [eulalie.util :as util]
            [eulalie.sign :as sign]
            [eulalie.util.xml :as x]
            [eulalie.platform :as platform]
            [eulalie.platform.xml :as platform.xml]
            [eulalie.util.query :as q]
            [plumbing.core :refer [update-in-when]]))

(derive :eulalie.service/ses :eulalie.service.generic/xml-response)
(derive :eulalie.service/ses :eulalie.service.generic/query-request)

(defn wrap-content [k attr]
  (let [{:keys [encoding data]} (if (string? attr)
                                  {:encoding "UTF-8" :data attr}
                                  attr)]
    {(conj k :encoding) encoding
     (conj k :data) data}))

(defn wrap-email-content [{:keys [text html subject] :as m}]
  (merge m
         (wrap-content [:message :subject] subject)
         (when html (wrap-content [:message :body :html] html))
         (when text (wrap-content [:message :body :text] text))))

(def target->seq-spec
  {:send-email
   {:to [:list [[:destination :to-addresses] :member]]
    :cc [:list [[:destination :cc-addresses] :member]]
    :bcc [:list [[:destination :bcc-addresses] :member]]
    :reply-to [:list [:reply-to-addresses :member]]}
   })

(def service-name "email")

(def service-defaults
  {:version "2010-12-01"
   :region "us-east-1"
   :service-name service-name
   :max-retries 3})

(defmethod eulalie/prepare-request :eulalie.service/ses [{:keys [target] :as req}]
  (let [{:keys [body] :as req} (q/prepare-query-request service-defaults req)]
    (assoc req
           :service-name service-name
           :body (cond-> (q/expand-sequences body (target->seq-spec target))
                   (= target :send-email) wrap-email-content))))

(def target->elem-spec
  {:send-email [:one :message-id]})

(defmethod eulalie/transform-response-body :eulalie.service/ses
  [{{:keys [target]} :request body :body}]
  (let [elem (platform.xml/string->xml-map body)]
    (x/extract-response-value target elem target->elem-spec)))
