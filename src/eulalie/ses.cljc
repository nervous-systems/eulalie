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

(defn wrap-content [attr]
  (if (string? attr)
    {:encoding "UTF-8" :data attr}
    attr))

(defn wrap-email-content [{:keys [text html subject] :as m}]
  (assoc m :message {:subject (wrap-content subject)
                     :body (cond-> {}
                             text (assoc :text (wrap-content text))
                             html (assoc :html (wrap-content html))
                             )}))

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

(defmethod eulalie/transform-response-body :eulalie.service/ses
  [{{:keys [target]} :request body :body}]
  (let [elem (platform.xml/string->xml-map body)]
    elem))


;; params {:Source "info@unclipapp.com"
;;         :Destination {:ToAddresses [email]}
;;         :Message {:Subject {:Data subject}
;;                   :Body {:Html {:Data email-body}}}}
