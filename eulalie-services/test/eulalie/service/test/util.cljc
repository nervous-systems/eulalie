(ns eulalie.service.test.util
  (:require [eulalie.service :as service]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [#?(:clj clojure.spec :cljs cljs.spec) :as s]
            [clojure.walk :as walk]))

(s/check-asserts true)

(defn gen-request [service targets]
  (gen/bind
    (gen/elements
     (for [target targets]
       (keyword (str "eulalie.service." (name service) ".request.target")
                (name target))))
    (fn [t]
      (gen/tuple (gen/return t) (s/gen t)))))

(defn request-roundtrip-property [service targets]
  (prop/for-all* [(gen-request service targets)]
    (fn [[target input-body]]
      (let [input-body (or input-body {})
            service    (keyword "eulalie.service" (name service))
            req-body   (service/transform-request-body
                        {:service service
                         :target  (keyword (name target))
                         :body    input-body})
            resp-body  (service/transform-response-body
                        {:request {:service service}
                         :body    req-body})]
        (= resp-body (walk/keywordize-keys input-body))))))
