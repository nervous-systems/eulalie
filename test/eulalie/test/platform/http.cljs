(ns eulalie.test.platform.http
  (:require [glossop.core]
            [cljs.nodejs :as nodejs]
            [eulalie.platform.util :refer [channel-fn]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [glossop.macros :refer [go-catching <?]]))

(def portfinder (nodejs/require "portfinder"))
(def http       (nodejs/require "http"))

(defn start-local-server! [f]
  (go-catching
    (let [port   (<? (channel-fn #(.getPort portfinder %)))
          server (.createServer
                  http
                  (fn [req resp]
                    (go-catching
                      (let [headers (js->clj (aget req "headers") :keywordize-keys true)
                            req {:headers headers}
                            {:keys [status headers body]} (<? (f req))]
                        (.writeHead resp status (clj->js headers))
                        (.end     resp body)))))]
      (.listen server port)
      {:port port :stop! #(.close server)})))
