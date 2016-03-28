(ns eulalie.test.elastic-transcoder
  (:require [clojure.string :as str]
            [eulalie.core :as eulalie]
            [eulalie.elastic-transcoder]
            [eulalie.support :as support]
            [eulalie.util.json :as util.json]
            [eulalie.test.common :as test.common
             #? (:clj :refer :cljs :refer-macros) [deftest is]]
            [glossop.core #? (:clj :refer :cljs :refer-macros) [go-catching <?]]))

(defn transcoder! [creds target body & [req-overrides]]
  (support/issue-request!
   (merge
    {:service     :elastic-transcoder
     :target      target
     :max-retries 0
     :body        body
     :creds       creds}
    req-overrides)))

(defn with-response! [target req cont]
  (test.common/with-aws
    (fn [creds]
      (go-catching
        (let [response (<? (transcoder! creds target req))]
          (cont response))))))

(deftest ^:integration jobs-by-status
  (with-response! :jobs-by-status {:status :progressing :ascending true}
    (fn [resp]
      (is (resp :jobs))
      (is (contains? resp :next-page-token)))))

(deftest ^:integration pipelines
  (with-response! :pipelines {:ascending true}
    (fn [{:keys [pipelines]}]
      (is pipelines)
      (when-first [pipeline pipelines]
        (is (pipeline :role))
        (is (pipeline :name))))))

(deftest ^:integration presets
  (with-response! :presets {:ascending false}
    (fn [{[preset] :presets}]
      (is (preset :id)))))

(deftest preset-delete
  (let [{method :method {path :path} :endpoint}
        (eulalie/prepare-request
         {:service :elastic-transcoder
          :target  :preset
          :body    {:preset :ok}
          :method  :delete})]
    (is (= method :delete))
    (is (str/ends-with? path "/preset/ok"))))

(deftest job-create
  (let [body
        (util.json/map-request-keys
         {:service :elastic-transcoder
          :method   :post
          :target   :jobs
          :body     {:input       {:encryption {:key-md5 "..." :mode :s3-aws-kms}}
                     :outputs     [{:watermarks [{:key-md5 "..."}]}]
                     :captions    {:merge-policy :merge-override
                                   :caption-formats [{:format :cea-708}]}
                     :pipeline-id "preserve-case"}})]
    (is (= body
           '{:Input      {:Encryption {:KeyMd5 "..." :Mode "S3-AWS-KMS"}}
             :Outputs    ({:Watermarks ({:KeyMd5 "..."})})
             :Captions   {:MergePolicy "MergeOverride"
                          :CaptionFormats ({:Format :cea-708})}
             :PipelineId "preserve-case"}))))
