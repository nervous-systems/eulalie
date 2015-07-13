(ns eulalie.test.platform.http
  (:require [org.httpkit.server :as http]
            [glossop.core :refer [go-catching]]))

(defn start-local-server! [f]
  (let [stop-server (http/run-server f {:port 0})
        {:keys [local-port]} (meta stop-server)]
    ;; Our cljs conterpart is asynchronous
    (go-catching {:port local-port :stop! stop-server})))

