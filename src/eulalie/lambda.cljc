(ns eulalie.lambda
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as csk-extras]
            [cemerick.url :as url]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [eulalie.core :as eulalie]
            [eulalie.platform :as platform]
            [eulalie.util.query :as util.query]
            [eulalie.util.service :as util.service]))

(def service-name "lambda")
(def service-version "2015-03-31")

(def service-defaults
  {:version service-version
   :region "us-east-1"
   :service-name service-name
   :max-retries 3})

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

(def fn-url [service-version "functions" ::name])
(def versioned-fn-url (into fn-url ["versions" ::version]))

(def target->url
  {:add-permission  [:post (conj versioned-fn-url "policy")]
   :get-function    [:get  versioned-fn-url]
   :invoke          [:post (conj fn-url "invocations")]
   :create-function [:post [service-version "functions"]]
   :delete-function [:delete fn-url]})

(defmulti prepare-request :target)

(defmethod prepare-request :add-permission
  [{{:keys [function-name] :as body} :body endpoint :endpoint :as req}]
  (assoc req
    :body (->>
           (dissoc body :function-name)
           (util.query/transform-policy-clause util.query/policy-key-out)
           (csk-extras/transform-keys csk/->PascalCaseString))))

(defmethod prepare-request :get-function
  [{{:keys [function-name]} :body endpoint :endpoint :as req}]
  (assoc req :body {}))

(defmethod prepare-request :invoke
  [{{:keys [invocation-type payload] :as body} :body endpoint :endpoint :as req}]
  (with-meta
    (assoc req
      :body payload
      :headers (body->headers body))
    {:eulalie.lambda/invocation-type invocation-type}))

(defmethod prepare-request :create-function
  [req]
  (update req :body #(csk-extras/transform-keys csk/->PascalCaseString %1)))

(defmethod prepare-request :delete-function
  [req]
  req)

(defn- build-endpoint
  [{:keys [endpoint target] {fn-name :function-name :as body} :body :as req}]
  (let [[method template] (target->url target)
        endpoint          (apply
                           url/url endpoint
                           (walk/prewalk-replace
                            {::name    (name fn-name)
                             ::version "HEAD"}
                            template))]
    (cond-> endpoint
      (body :qualifier) (assoc :query {:Qualifier (body :qualifier)}))))

(defmethod eulalie/prepare-request :eulalie.service/lambda [req]
  (let [req (util.service/default-request service-defaults req)]
    (prepare-request
     (assoc req
       :method       (req :method)
       :endpoint     (build-endpoint req)
       :service-name service-name))))

(defmethod eulalie/transform-request-body :eulalie.service/lambda
  [{:keys [body] :as req}]
  (cond-> body (not (string? body)) platform/encode-json))

(defn function-error [{{:keys [x-amz-function-error]} :headers body :body :as x}]
  (when x-amz-function-error
    {:type (csk/->kebab-case-keyword x-amz-function-error)
     :message (:errorMessage body)}))

(defn parse-log-result [m]
  (-> m platform/decode-base64 (str/split #"\n")))

(defn was-request-response? [x]
  (-> x meta :eulalie.lambda/invocation-type (= :request-response)))

(defmulti transform-response-body (fn [{{:keys [target]} :request}] target))
(defmethod transform-response-body :add-permission [{:keys [body] :as resp}]
  (let [{:keys [statement]} (util.query/nested-json-in body)]
    (->> statement
         platform/decode-json
         (csk-extras/transform-keys util.query/policy-key-in)
         (util.query/transform-policy-clause util.query/policy-key-in))))

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

(defmethod transform-response-body :default [{:keys [body]}]
  (->> body
       platform/decode-json
       (csk-extras/transform-keys csk/->kebab-case-keyword)))

(defmethod eulalie/transform-response-body :eulalie.service/lambda
  [{:keys [headers request] :as response}]
  (transform-response-body response))

(defmethod eulalie/transform-response-error :eulalie.service/lambda
  ;; We're assuming the type was extracted from the headers
  [{body :body error :error}]
  (assoc error :message (-> body platform/decode-json :message)))
