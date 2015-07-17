(ns eulalie.lambda
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as csk-extras]
            [cemerick.url :as url]
            [eulalie.platform :as platform]
            [clojure.string :as str]
            [eulalie.core :as eulalie]
            [eulalie.util.service :as util.service]))

;; (def target-methods {:remove-permission :delete})
;; (defn target->method [target]
;;   (or (target-methods target)
;;       (let [verb (-> target name (util/to-first-match "-"))]
;;         ({"update" :put "get" :get "list" :get} verb :post))))

;; (def simple-json #{:create-event-source-mapping
;;                    :update-event-source-mapping
;;                    :update-function-code
;;                    :update-function-configuration})

(def service-name "lambda")
(def service-version "2015-03-31")

(def service-defaults
  {:version service-version
   :region "us-east-1"
   :service-name service-name
   :max-retries 3})

;; (defn prepare-body [target body]
;;   (cond (simple-json target)
;;         (csk-extras/transform-keys csk/->PascalCase body)
;;         (= target :invoke)
;;         ()))

(defn client-context->b64 [m]
  (-> m platform/encode-json platform/encode-base64))

(defn body->headers [{:keys [client-context invocation-type log-type]}]
  (cond-> {}
    client-context  (assoc "X-Amz-Client-Context"
                           (cond-> client-context
                             (map? client-context) client-context->b64))
    invocation-type (assoc "X-Amz-Invocation-Type"
                           (csk/->PascalCaseString invocation-type))
    log-type        (assoc "X-Amz-Log-Type"
                           (csk/->PascalCaseString log-type))))

(defmulti prepare-request :target)

(defmethod prepare-request :get-function
  [{{:keys [function-name]} :body endpoint :endpoint :as req}]
  (assoc req
         :endpoint (url/url
                    endpoint service-version "functions"
                    (name function-name) "versions" "HEAD")
         :method :get
         :body {}))

(defmethod prepare-request :invoke
  [{{:keys [invocation-type payload function-name]} :body endpoint :endpoint :as req}]
  (with-meta
    (assoc req
           :endpoint (url/url
                      endpoint service-version "functions"
                      (name function-name) "invocations")
           :body payload
           :method :post)
    {:eulalie.lambda/invocation-type invocation-type}))

(defmethod eulalie/prepare-request :eulalie.service/lambda
  [{:keys [target body] :as req}]
  (let [{:keys [endpoint] :as req}
        (util.service/default-request service-defaults req)]
    (-> req
        (update-in [:headers] merge (body->headers body))
        prepare-request
        (assoc :service-name service-name))))

(defmethod eulalie/transform-request-body :eulalie.service/lambda
  [{:keys [body]}]
  (cond-> body (not (string? body)) platform/encode-json))

(defn function-error [{{:keys [x-amz-function-error]} :headers body :body}]
  (when x-amz-function-error
    {:type (csk/->kebab-case-keyword x-amz-function-error)
     :message (:errorMessage body)}))

(defn parse-log-result [m]
  (-> m platform/decode-base64 (str/split #"\n")))

(defn was-request-response? [x]
  (-> x meta :eulalie.lambda/invocation-type (= :request-response)))

(defmulti transform-response-body (fn [{{:keys [target]} :request}] target))
(defmethod transform-response-body :invoke
  [{:keys [headers request] :as response}]
  (let [{:keys [body] :as response}
        (if (was-request-response? request)
          (update-in response [:body] platform/decode-json)
          response)
        response (if-let [error (function-error response)]
                   [:error error]
                   [:ok    body])]
    (with-meta
      response
      {:log-result (some-> headers :x-amz-log-result parse-log-result)})))

(defmethod transform-response-body :get-function [{:keys [body]}]
  (->> body
       platform/decode-json
       (csk-extras/transform-keys csk/->kebab-case-keyword)))

(defmethod eulalie/transform-response-body :eulalie.service/lambda
  [{:keys [headers request] :as response}]
  (transform-response-body response))

(defmethod eulalie/transform-response-error :eulalie.service/lambda [_] nil)
