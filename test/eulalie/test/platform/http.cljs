(ns eulalie.test.platform.http
  (:require [glossop.core]
            [cljs.nodejs :as nodejs]
            [eulalie.platform.util :refer [channel-fn]]
            [glossop.core :refer-macros [go-catching <?]]))

(def portfinder (nodejs/require "portfinder"))
(def http       (nodejs/require "http"))

(defn handler [f req resp]
  (go-catching
    (let [headers (js->clj (aget req "headers") :keywordize-keys true)
          {:keys [status headers body]} (<? (f {:headers headers}))]
      (.writeHead resp status (clj->js headers))
      (.end     resp body))))

(defn start-local-server! [f]
  (go-catching
    (let [port   (<? (channel-fn #(.getPort portfinder %)))
          server (.createServer http (partial handler f))]
      (.listen server port)
      {:port port :stop! #(.close server)})))
