(ns eulalie.lambda
  (:require [base64-clj.core :as base64]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as csk-extras]
            [cemerick.url :as url]
            [cheshire.core :as json]
            [clojure.string :as str]
            [eulalie]
            [eulalie.service-util :as service-util]))

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
  (-> m json/encode base64/encode))

(defn body->headers [{:keys [client-context invocation-type log-type]}]
  (cond-> {}
    client-context  (assoc "X-Amz-Client-Context"
                           (cond-> client-context
                             (map? client-context) client-context->b64))
    invocation-type (assoc "X-Amz-Invocation-Type"
                           (csk/->PascalCaseString invocation-type))
    log-type        (assoc "X-Amz-Log-Type"
                           (csk/->PascalCaseString log-type))))

(defmethod eulalie/prepare-request :eulalie.service/lambda
  [{target :target
    {:keys [invocation-type payload function-name] :as body} :body :as req}]
  (let [{:keys [endpoint] :as req}
        (service-util/default-request service-defaults req)
        endpoint (url/url
                  endpoint service-version "functions"
                  (name function-name) "invocations")]
    (-> req
        (update-in [:headers] merge (body->headers body))
        (assoc
         :endpoint endpoint
         :service-name service-name
         :method :post
         :body payload)
        (with-meta {:eulalie.lambda/invocation-type invocation-type}))))

(defmethod eulalie/transform-request-body :eulalie.service/lambda
  [{:keys [body]}]
  (cond-> body (not (string? body)) json/encode))

(defn function-error [{{:keys [x-amz-function-error]} :headers body :body}]
  (when x-amz-function-error
    {:type (csk/->kebab-case-keyword x-amz-function-error)
     :message (:errorMessage body)}))

(defn parse-log-result [m]
  (-> m base64/decode (str/split #"\n")))

(defn was-request-response? [x]
  (-> x meta :eulalie.lambda/invocation-type (= :request-response)))

(defmethod eulalie/transform-response-body :eulalie.service/lambda
  [{:keys [headers request] :as response}]
  (let [{:keys [body] :as response}
        (if (was-request-response? request)
          (update-in response [:body] json/decode true)
          response)
        response (if-let [error (function-error response)]
                   [:error error]
                   [:ok    body])]
    (with-meta
      response
      {:log-result (some-> headers :x-amz-log-result parse-log-result)})))

(defmethod eulalie/transform-response-error :eulalie.service/lambda [_] nil)
